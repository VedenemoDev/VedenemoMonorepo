import { type FormEvent, type KeyboardEvent, useEffect, useRef, useState } from "react";
import { ModelChangeEventAdapter } from "./adapters/ModelChangeEventAdapter";
import { PlantUmlModelAdapter } from "./adapters/PlantUmlModelAdapter";

type ModelLoadState = "idle" | "loading" | "ok" | "error";
type ModelConnectionState = "disconnected" | "connecting" | "connected" | "error";
type ConsoleStatus = "loading" | "ready" | "error";

type RuntimeConfig = {
  apiBaseUrl?: string;
};

type ModelSummary = {
  azName: string;
  visName: string;
  version: string;
};

type ConsoleSessionResponse = {
  sessionId: string;
  backendSessionId: string;
  prompt: string;
  attachedModelAzName?: string | null;
};

type ConsoleCommandResponse = {
  status: string;
  outputLines: string[];
  prompt: string;
  attachedModelAzName?: string | null;
};

const PLANTUML_TARGET_ID = "plantuml-diagram";
const CONNECTED_MODEL_STORAGE_KEY = "vedenemo.connectedModelAzName";

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

function readConnectedModelAzName(): string {
  const queryValue = new URLSearchParams(window.location.search).get("connectedModelAzName");
  if (queryValue) {
    return queryValue;
  }
  return window.sessionStorage.getItem(CONNECTED_MODEL_STORAGE_KEY) ?? "";
}

function ConsolePage() {
  const sessionIdRef = useRef("");
  const apiBaseUrlRef = useRef("");
  const commandInputRef = useRef<HTMLInputElement>(null);
  const [apiBaseUrl, setApiBaseUrl] = useState("");
  const [session, setSession] = useState<ConsoleSessionResponse | null>(null);
  const [status, setStatus] = useState<ConsoleStatus>("loading");
  const [statusMessage, setStatusMessage] = useState("Starting console session...");
  const [history, setHistory] = useState<string[]>([]);
  const [commandHistory, setCommandHistory] = useState<string[]>([]);
  const [commandHistoryIndex, setCommandHistoryIndex] = useState(0);
  const [command, setCommand] = useState("");
  const [isExecuting, setIsExecuting] = useState(false);

  useEffect(() => {
    let cancelled = false;

    loadRuntimeConfig()
      .then(async (config) => {
        const baseUrl = normalizeBaseUrl(config.apiBaseUrl ?? "");
        if (!baseUrl) {
          throw new Error("Backend URL is not configured");
        }
        const connectedModelAzName = readConnectedModelAzName();
        const response = await fetch(`${baseUrl}/console/sessions`, {
          method: "POST",
          headers: {
            Accept: "application/json",
            "Content-Type": "application/json",
          },
          body: JSON.stringify({ connectedModelAzName: connectedModelAzName || null }),
        });
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }
        const body = (await response.json()) as ConsoleSessionResponse;
        if (cancelled) {
          void fetch(`${baseUrl}/console/sessions/${body.sessionId}`, { method: "DELETE" });
          return;
        }
        sessionIdRef.current = body.sessionId;
        apiBaseUrlRef.current = baseUrl;
        setApiBaseUrl(baseUrl);
        setSession(body);
        setStatus("ready");
        setStatusMessage(body.attachedModelAzName ? `Attached to ${body.attachedModelAzName}` : "No model attached");
        setHistory([
          "Vedenemo web console",
          body.attachedModelAzName ? `Attached model: ${body.attachedModelAzName}` : "No connected model was provided.",
        ]);
      })
      .catch((error) => {
        if (!cancelled) {
          setStatus("error");
          setStatusMessage(error instanceof Error ? error.message : "Console session start failed");
        }
      });

    return () => {
      cancelled = true;
      const sessionId = sessionIdRef.current;
      const baseUrl = apiBaseUrlRef.current;
      if (baseUrl && sessionId) {
        void fetch(`${baseUrl}/console/sessions/${sessionId}`, { method: "DELETE" });
      }
    };
  }, []);

  useEffect(() => {
    if (status === "loading" || isExecuting || session === null) {
      return;
    }
    const animationFrameId = window.requestAnimationFrame(() => {
      commandInputRef.current?.focus({ preventScroll: true });
    });
    return () => window.cancelAnimationFrame(animationFrameId);
  }, [status, isExecuting, session]);

  async function executeConsoleCommand(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!apiBaseUrl || !session || isExecuting) {
      return;
    }

    const trimmedCommand = command.trim();
    if (!trimmedCommand) {
      return;
    }

    setCommand("");
    setCommandHistory((current) => [...current, trimmedCommand]);
    setCommandHistoryIndex(commandHistory.length + 1);
    setIsExecuting(true);
    setHistory((current) => [...current, `${session.prompt} ${trimmedCommand}`]);

    try {
      const response = await fetch(`${apiBaseUrl}/console/sessions/${session.sessionId}/commands`, {
        method: "POST",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ command: trimmedCommand }),
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const body = (await response.json()) as ConsoleCommandResponse;
      setSession((current) => current === null
        ? current
        : {
            ...current,
            prompt: body.prompt,
            attachedModelAzName: body.attachedModelAzName,
          });
      setHistory((current) => [...current, ...body.outputLines]);
      setStatusMessage(body.attachedModelAzName ? `Attached to ${body.attachedModelAzName}` : "No model attached");
    } catch (error) {
      setHistory((current) => [...current, error instanceof Error ? error.message : "Command failed"]);
      setStatus("error");
      setStatusMessage(error instanceof Error ? error.message : "Command failed");
    } finally {
      setIsExecuting(false);
    }
  }

  function focusCommandInput() {
    if (status === "loading" || isExecuting || session === null) {
      return;
    }
    commandInputRef.current?.focus({ preventScroll: true });
  }

  function navigateCommandHistory(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Escape") {
      event.preventDefault();
      setCommand("");
      setCommandHistoryIndex(commandHistory.length);
      setStatusMessage("Command entry cancelled");
      focusCommandInput();
      return;
    }
    const isPrevious = event.key === "ArrowUp" || (event.ctrlKey && event.key.toLowerCase() === "p");
    const isNext = event.key === "ArrowDown" || (event.ctrlKey && event.key.toLowerCase() === "n");
    if (!isPrevious && !isNext) {
      return;
    }
    if (commandHistory.length === 0) {
      return;
    }
    event.preventDefault();
    if (isPrevious) {
      const nextIndex = Math.max(0, commandHistoryIndex - 1);
      setCommandHistoryIndex(nextIndex);
      setCommand(commandHistory[nextIndex]);
      return;
    }
    const nextIndex = Math.min(commandHistory.length, commandHistoryIndex + 1);
    setCommandHistoryIndex(nextIndex);
    setCommand(nextIndex === commandHistory.length ? "" : commandHistory[nextIndex]);
  }

  return (
    <main className="console-shell">
      <header className="console-header">
        <div>
          <h1>Vedenemo Console</h1>
          <span className={`console-status console-status-${status}`}>{statusMessage}</span>
          <span className="console-shortcut-hint">Esc cancels the current prompt or input.</span>
        </div>
        <a className="secondary-link" href="/">
          Model diagram
        </a>
      </header>
      <section className="console-surface" aria-label="Vedenemo virtual CLI" onMouseDown={focusCommandInput}>
        <div className="console-output" aria-live="polite">
          {history.map((line, index) => (
            <div key={`${index}-${line}`} className="console-line">
              {line || "\u00a0"}
            </div>
          ))}
        </div>
        <form className="console-input-row" onSubmit={(event) => void executeConsoleCommand(event)}>
          <label htmlFor="console-command">{session?.prompt ?? "VedenemoCli>"}</label>
          <input
            ref={commandInputRef}
            id="console-command"
            value={command}
            onChange={(event) => {
              setCommand(event.target.value);
              setCommandHistoryIndex(commandHistory.length);
            }}
            onKeyDown={navigateCommandHistory}
            disabled={status === "loading" || isExecuting || session === null}
            autoComplete="off"
            autoFocus
          />
          <button type="submit" disabled={status === "loading" || isExecuting || session === null}>
            Run
          </button>
        </form>
      </section>
    </main>
  );
}

export function App() {
  if (window.location.pathname === "/console") {
    return <ConsolePage />;
  }

  const eventAdapterRef = useRef(new ModelChangeEventAdapter());
  const plantUmlDiagramRendererRef = useRef<import("./adapters/PlantUmlDiagramRendererAdapter").PlantUmlDiagramRendererAdapter | null>(null);
  const plantUmlAdapterRef = useRef(new PlantUmlModelAdapter());
  const selectedModelAzNameRef = useRef("");
  const [apiBaseUrl, setApiBaseUrl] = useState("");
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
        window.sessionStorage.setItem(CONNECTED_MODEL_STORAGE_KEY, selectedModelAzName);
      },
      onClose: () => {
        setModelConnectionState("disconnected");
        setModelConnectionMessage("Disconnected");
        window.sessionStorage.removeItem(CONNECTED_MODEL_STORAGE_KEY);
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

  return (
    <main className="app-shell">
      <section className="card">
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
            <a
              className="console-link"
              href={modelConnectionState === "connected" && selectedModelAzName
                ? `/console?connectedModelAzName=${encodeURIComponent(selectedModelAzName)}`
                : "/console"}
            >
              Console
            </a>
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
