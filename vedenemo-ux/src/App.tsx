import { useEffect, useRef, useState } from "react";
import { ModelChangeEventAdapter } from "./adapters/ModelChangeEventAdapter";
import { PlantUmlModelAdapter } from "./adapters/PlantUmlModelAdapter";

type PingState = "idle" | "loading" | "ok" | "error";
type ModelLoadState = "idle" | "loading" | "ok" | "error";
type ModelConnectionState = "disconnected" | "connecting" | "connected" | "error";

type RuntimeConfig = {
  apiBaseUrl?: string;
};

type ModelSummary = {
  azName: string;
  visName: string;
  version: string;
};

const PLANTUML_TARGET_ID = "plantuml-diagram";

async function loadRuntimeConfig(): Promise<RuntimeConfig> {
  const response = await fetch("/config.json", { cache: "no-store" });
  if (!response.ok) {
    return {};
  }
  return response.json() as Promise<RuntimeConfig>;
}

function normalizeBaseUrl(value: string): string {
  return value.replace(/\/+$/, "");
}

async function fetchModels(apiBaseUrl: string): Promise<ModelSummary[]> {
  const response = await fetch(`${apiBaseUrl}/models/list`, {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  return response.json() as Promise<ModelSummary[]>;
}

export function App() {
  const eventAdapterRef = useRef(new ModelChangeEventAdapter());
  const plantUmlDiagramRendererRef = useRef<import("./adapters/PlantUmlDiagramRendererAdapter").PlantUmlDiagramRendererAdapter | null>(null);
  const plantUmlAdapterRef = useRef(new PlantUmlModelAdapter());
  const selectedModelAzNameRef = useRef("");
  const [apiBaseUrl, setApiBaseUrl] = useState("");
  const [pingState, setPingState] = useState<PingState>("idle");
  const [pingMessage, setPingMessage] = useState("Not checked");
  const [modelLoadState, setModelLoadState] = useState<ModelLoadState>("idle");
  const [modelMessage, setModelMessage] = useState("Models not loaded");
  const [models, setModels] = useState<ModelSummary[]>([]);
  const [selectedModelAzName, setSelectedModelAzName] = useState("");
  const [modelConnectionState, setModelConnectionState] = useState<ModelConnectionState>("disconnected");
  const [modelConnectionMessage, setModelConnectionMessage] = useState("Disconnected");
  const [diagramHasContent, setDiagramHasContent] = useState(false);
  const [diagramMessage, setDiagramMessage] = useState("Select model and connect to show diagram.");

  useEffect(() => {
    selectedModelAzNameRef.current = selectedModelAzName;
  }, [selectedModelAzName]);

  useEffect(() => {
    let cancelled = false;

    loadRuntimeConfig()
      .then((config) => {
        const baseUrl = normalizeBaseUrl(config.apiBaseUrl ?? "");
        if (cancelled) {
          return;
        }
        setApiBaseUrl(baseUrl);
        if (baseUrl) {
          void refreshModels(baseUrl, () => cancelled);
        } else {
          setModelLoadState("error");
          setModelMessage("Backend URL is not configured");
        }
      })
      .catch(() => {
        if (!cancelled) {
          setPingState("error");
          setPingMessage("Backend config unavailable");
          setModelLoadState("error");
          setModelMessage("Backend config unavailable");
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    return () => {
      eventAdapterRef.current.disconnect();
    };
  }, []);

  useEffect(() => {
    if (modelConnectionState === "connected" && selectedModelAzName) {
      void renderSelectedModel(selectedModelAzName);
    }
  }, [modelConnectionState, selectedModelAzName]);

  async function refreshModels(baseUrl = apiBaseUrl, isCancelled: () => boolean = () => false) {
    if (!baseUrl) {
      setModelLoadState("error");
      setModelMessage("Backend URL is not configured");
      return;
    }

    setModelLoadState("loading");
    setModelMessage("Loading models...");

    try {
      const nextModels = await fetchModels(baseUrl);
      if (isCancelled()) {
        return;
      }
      setModels(nextModels);
      setSelectedModelAzName((current) => {
        if (nextModels.some((model) => model.azName === current)) {
          return current;
        }
        return nextModels[0]?.azName ?? "";
      });
      setModelLoadState("ok");
      setModelMessage(nextModels.length === 0 ? "No models available" : `${nextModels.length} model${nextModels.length === 1 ? "" : "s"} loaded`);
    } catch (error) {
      if (isCancelled()) {
        return;
      }
      setModelLoadState("error");
      setModelMessage(error instanceof Error ? error.message : "Model refresh failed");
    }
  }

  async function renderSelectedModel(modelAzName = selectedModelAzName) {
    if (!apiBaseUrl) {
      setDiagramHasContent(false);
      setDiagramMessage("Backend URL is not configured.");
      return;
    }
    if (!modelAzName) {
      setDiagramHasContent(false);
      setDiagramMessage("Select model and connect to show diagram.");
      return;
    }

    try {
      setDiagramHasContent(false);
      setDiagramMessage("Rendering diagram...");
      const selectedModel = models.find((model) => model.azName.toLocaleLowerCase() === modelAzName.toLocaleLowerCase());
      const plantUmlSource = await plantUmlAdapterRef.current.renderModel(
        apiBaseUrl,
        modelAzName,
        selectedModel?.visName,
      );
      await renderPlantUmlSvg(plantUmlSource);
      setDiagramHasContent(true);
      setDiagramMessage("Diagram rendered");
    } catch (error) {
      setDiagramHasContent(false);
      setDiagramMessage(error instanceof Error ? `Diagram render failed: ${error.message}` : "Diagram render failed.");
    }
  }

  async function renderPlantUmlSvg(plantUmlSource: string): Promise<void> {
    if (plantUmlDiagramRendererRef.current === null) {
      const module = await import("./adapters/PlantUmlDiagramRendererAdapter");
      plantUmlDiagramRendererRef.current = new module.PlantUmlDiagramRendererAdapter();
    }
    return plantUmlDiagramRendererRef.current.renderSvg(plantUmlSource, PLANTUML_TARGET_ID);
  }

  async function toggleModelConnection() {
    if (modelConnectionState === "connected" || modelConnectionState === "connecting") {
      eventAdapterRef.current.disconnect();
      setModelConnectionState("disconnected");
      setModelConnectionMessage("Disconnected");
      return;
    }

    if (!apiBaseUrl) {
      setModelConnectionState("error");
      setModelConnectionMessage("Backend URL is not configured");
      return;
    }
    if (!selectedModelAzName) {
      setModelConnectionState("error");
      setModelConnectionMessage("Select model before connecting");
      return;
    }

    setModelConnectionState("connecting");
    setModelConnectionMessage("Connecting...");
    await renderSelectedModel(selectedModelAzName);

    eventAdapterRef.current.connect(apiBaseUrl, {
      onOpen: () => {
        setModelConnectionState("connected");
        setModelConnectionMessage("Connected");
      },
      onClose: () => {
        setModelConnectionState("disconnected");
        setModelConnectionMessage("Disconnected");
      },
      onError: (message) => {
        setModelConnectionState("error");
        setModelConnectionMessage(message);
      },
      onModelChanged: (modelAzName) => {
        void refreshModels();
        if (modelAzName.toLowerCase() === selectedModelAzNameRef.current.toLowerCase()) {
          void renderSelectedModel(modelAzName);
        }
      },
    });
  }

  async function pingBackend() {
    if (!apiBaseUrl) {
      setPingState("error");
      setPingMessage("Backend URL is not configured");
      return;
    }

    setPingState("loading");
    setPingMessage("Pinging...");

    try {
      const response = await fetch(`${apiBaseUrl}/models/ping`, {
        headers: {
          Accept: "application/json",
        },
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const body = (await response.json()) as { status?: string };
      setPingState("ok");
      setPingMessage(body.status === "ok" ? "Backend responded OK" : "Backend responded");
    } catch (error) {
      setPingState("error");
      setPingMessage(error instanceof Error ? error.message : "Ping failed");
    }
  }

  return (
    <main className="app-shell">
      <section className="card">
        <h1>Vedenemo UX Deployment Check</h1>
        <p>The Firebase Hosting pipeline is now serving updates from GitHub Actions.</p>
        <p>This page is still intentionally minimal while the backend skeleton settles.</p>
        <div className="ping-panel">
          <button type="button" onClick={pingBackend} disabled={pingState === "loading"}>
            Ping
          </button>
          <span className={`ping-status ping-status-${pingState}`}>{pingMessage}</span>
        </div>
        <div className="model-panel">
          <label htmlFor="model-select">Select model</label>
          <div className="model-controls">
            <select
              id="model-select"
              value={selectedModelAzName}
              onChange={(event) => setSelectedModelAzName(event.target.value)}
              disabled={modelLoadState === "loading" || models.length === 0}
            >
              {models.length === 0 ? (
                <option value="">No models available</option>
              ) : (
                models.map((model) => (
                  <option key={model.azName} value={model.azName}>
                    {model.visName} ({model.azName}) version {model.version}
                  </option>
                ))
              )}
            </select>
            <button type="button" onClick={() => void refreshModels()} disabled={modelLoadState === "loading"}>
              Refresh model list
            </button>
            <button
              type="button"
              className="connect-button"
              onClick={() => void toggleModelConnection()}
              disabled={modelConnectionState === "connecting"}
            >
              {modelConnectionState === "connected" ? "Disconnect" : "Connect"}
            </button>
          </div>
          <span className={`model-status model-status-${modelLoadState}`}>{modelMessage}</span>
          <span className={`connection-status connection-status-${modelConnectionState}`}>{modelConnectionMessage}</span>
          <span className="diagram-status">{diagramMessage}</span>
          <div className="diagram-viewport" aria-label="PlantUML class diagram">
            <div id={PLANTUML_TARGET_ID} className="diagram-svg" />
            {!diagramHasContent && (
              <div className="diagram-placeholder">{diagramMessage}</div>
            )}
          </div>
        </div>
        <p className="backend-url">{apiBaseUrl || "Backend URL is not configured"}</p>
      </section>
    </main>
  );
}
