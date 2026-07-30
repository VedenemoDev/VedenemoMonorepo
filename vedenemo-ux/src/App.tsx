import {
  type CSSProperties,
  type FormEvent,
  type KeyboardEvent,
  type PointerEvent,
  useEffect,
  useRef,
  useState,
} from "react";
import { ModelChangeEventAdapter } from "./adapters/ModelChangeEventAdapter";
import { PlantUmlModelAdapter } from "./adapters/PlantUmlModelAdapter";

type ModelLoadState = "idle" | "loading" | "ok" | "error";
type ModelConnectionState = "disconnected" | "connecting" | "connected" | "error";
type ConsoleStatus = "loading" | "ready" | "error";
type ActiveTab = "models" | "modelInstances";
type ModelInstanceLoadState = "idle" | "loading" | "ok" | "error";

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

type EntityDescription = {
  azName: string;
  visName: string;
};

type ApiDescriptionResponse = {
  modelAzName: string;
  modelVisName: string;
  entities: EntityDescription[];
};

type CountResponse = {
  count: number;
};

type ModelInstanceRootResponse = {
  instanceRootId: string;
  modelAzName: string;
  modelVersion: string;
  visName?: string | null;
};

type EntityInstanceGroup = {
  entityAzName: string;
  entityVisName: string;
  count?: number;
  error?: string;
};

type ModelInstanceRootNode = {
  instanceRootId: string;
  visName?: string | null;
  entityGroups: EntityInstanceGroup[];
};

type ModelInstanceModelNode = {
  modelAzName: string;
  modelVisName: string;
  roots: ModelInstanceRootNode[];
  error?: string;
};

type ConsolePanelProps = {
  connectedModelAzName?: string;
  mode: "page" | "pane";
};

type RenameDialogState = {
  modelAzName: string;
  instanceRootId: string;
  nextName: string;
};

const PLANTUML_TARGET_ID = "plantuml-diagram";
const CONNECTED_MODEL_STORAGE_KEY = "vedenemo.connectedModelAzName";
const CONSOLE_PANE_HEIGHT_STORAGE_KEY = "vedenemo.consolePaneHeight";
const DIAGRAM_EMPTY_MESSAGE = "Select model and connect to show diagram.";
const DIAGRAM_RENDERED_MESSAGE = "Diagram rendered";
const DEFAULT_CONSOLE_PANE_HEIGHT = 360;
const MIN_CONSOLE_PANE_HEIGHT = 256;
const MAX_CONSOLE_PANE_VIEWPORT_RATIO = 0.75;

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

async function fetchModelInstanceApi(apiBaseUrl: string, modelAzName: string): Promise<ApiDescriptionResponse> {
  const response = await fetch(`${apiBaseUrl}/data/${encodeURIComponent(modelAzName)}/_api`, {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  return response.json() as Promise<ApiDescriptionResponse>;
}

async function fetchEntityInstanceCount(apiBaseUrl: string, modelAzName: string, instanceRootId: string, entityAzName: string): Promise<number> {
  const response = await fetch(`${apiBaseUrl}/data/${encodeURIComponent(modelAzName)}/roots/${encodeURIComponent(instanceRootId)}/${encodeURIComponent(entityAzName)}/_count`, {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  const body = (await response.json()) as CountResponse;
  return body.count;
}

async function fetchModelInstanceRoots(apiBaseUrl: string, modelAzName: string): Promise<ModelInstanceRootResponse[]> {
  const response = await fetch(`${apiBaseUrl}/data/${encodeURIComponent(modelAzName)}/roots`, {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  return response.json() as Promise<ModelInstanceRootResponse[]>;
}

async function renameModelInstanceRoot(apiBaseUrl: string, modelAzName: string, instanceRootId: string, visName: string): Promise<ModelInstanceRootResponse> {
  const response = await fetch(`${apiBaseUrl}/data/${encodeURIComponent(modelAzName)}/roots/${encodeURIComponent(instanceRootId)}`, {
    method: "PUT",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ visName }),
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  return response.json() as Promise<ModelInstanceRootResponse>;
}

function readConnectedModelAzName(): string {
  return new URLSearchParams(window.location.search).get("connectedModelAzName") ?? "";
}

function rootDisplayName(root: ModelInstanceRootNode): string {
  return root.visName?.trim() || shortRootId(root.instanceRootId);
}

function rootResponseDisplayName(root: ModelInstanceRootResponse): string {
  return root.visName?.trim() || shortRootId(root.instanceRootId);
}

function shortRootId(instanceRootId: string): string {
  return instanceRootId.length <= 8 ? instanceRootId : instanceRootId.slice(0, 8);
}

function clampConsolePaneHeight(value: number): number {
  const maxHeight = Math.max(MIN_CONSOLE_PANE_HEIGHT, Math.floor(window.innerHeight * MAX_CONSOLE_PANE_VIEWPORT_RATIO));
  return Math.min(Math.max(value, MIN_CONSOLE_PANE_HEIGHT), maxHeight);
}

function readConsolePaneHeight(): number {
  const storedValue = Number(window.localStorage.getItem(CONSOLE_PANE_HEIGHT_STORAGE_KEY));
  if (!Number.isFinite(storedValue) || storedValue <= 0) {
    return clampConsolePaneHeight(DEFAULT_CONSOLE_PANE_HEIGHT);
  }
  return clampConsolePaneHeight(storedValue);
}

function ConsolePanel({ connectedModelAzName = "", mode }: ConsolePanelProps) {
  const sessionIdRef = useRef("");
  const apiBaseUrlRef = useRef("");
  const commandInputRef = useRef<HTMLInputElement>(null);
  const consoleOutputRef = useRef<HTMLDivElement>(null);
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
  }, [connectedModelAzName]);

  useEffect(() => {
    if (status === "loading" || isExecuting || session === null) {
      return;
    }
    const animationFrameId = window.requestAnimationFrame(() => {
      commandInputRef.current?.focus({ preventScroll: true });
    });
    return () => window.cancelAnimationFrame(animationFrameId);
  }, [status, isExecuting, session]);

  useEffect(() => {
    const output = consoleOutputRef.current;
    if (output === null) {
      return;
    }
    output.scrollTop = output.scrollHeight;
  }, [history]);

  async function executeConsoleCommand(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!apiBaseUrl || !session || isExecuting) {
      return;
    }

    const commandToSubmit = command.trim();
    if (!commandToSubmit && !isInteractivePrompt(session.prompt)) {
      return;
    }

    await submitConsoleCommand(commandToSubmit, true);
  }

  async function submitConsoleCommand(commandToSubmit: string, recordInCommandHistory: boolean) {
    if (!apiBaseUrl || !session || isExecuting) {
      return;
    }
    setCommand("");
    if (recordInCommandHistory && commandToSubmit) {
      setCommandHistory((current) => [...current, commandToSubmit]);
      setCommandHistoryIndex(commandHistory.length + 1);
    } else {
      setCommandHistoryIndex(commandHistory.length);
    }
    setIsExecuting(true);
    if (recordInCommandHistory) {
      setHistory((current) => [...current, `${session.prompt} ${commandToSubmit}`]);
    }

    try {
      const response = await fetch(`${apiBaseUrl}/console/sessions/${session.sessionId}/commands`, {
        method: "POST",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ command: commandToSubmit }),
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

  function isInteractivePrompt(prompt: string): boolean {
    return !prompt.startsWith("VedenemoCli");
  }

  function navigateCommandHistory(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Escape") {
      event.preventDefault();
      void submitConsoleCommand("\u001b", false);
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

  const inputId = mode === "page" ? "console-command" : "console-pane-command";
  const panelContent = (
    <>
      <header className="console-header">
        <div>
          <h1>Vedenemo Console</h1>
          <span className={`console-status console-status-${status}`}>{statusMessage}</span>
          <span className="console-shortcut-hint">Esc cancels the current prompt or input.</span>
        </div>
        {mode === "page" && (
          <a className="secondary-link" href="/">
            Model diagram
          </a>
        )}
      </header>
      <section className="console-surface" aria-label="Vedenemo virtual CLI" onMouseDown={focusCommandInput}>
        <div ref={consoleOutputRef} className="console-output" aria-live="polite">
          {history.map((line, index) => (
            <div key={`${index}-${line}`} className="console-line">
              {line || "\u00a0"}
            </div>
          ))}
        </div>
        <form className="console-input-row" onSubmit={(event) => void executeConsoleCommand(event)}>
          <label htmlFor={inputId}>{session?.prompt ?? "VedenemoCli>"}</label>
          <input
            ref={commandInputRef}
            id={inputId}
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
    </>
  );

  if (mode === "pane") {
    return <div className="console-pane-shell">{panelContent}</div>;
  }

  return (
    <main className="console-shell">
      {panelContent}
    </main>
  );
}

function ConsolePage() {
  return <ConsolePanel connectedModelAzName={readConnectedModelAzName()} mode="page" />;
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
  const [diagramHasContent, setDiagramHasContent] = useState(false);
  const [diagramMessage, setDiagramMessage] = useState(DIAGRAM_EMPTY_MESSAGE);
  const [isConsolePaneOpen, setIsConsolePaneOpen] = useState(false);
  const [consolePaneHeight, setConsolePaneHeight] = useState(readConsolePaneHeight);
  const [activeTab, setActiveTab] = useState<ActiveTab>("models");
  const [modelInstanceLoadState, setModelInstanceLoadState] = useState<ModelInstanceLoadState>("idle");
  const [modelInstanceMessage, setModelInstanceMessage] = useState("Model instances not loaded");
  const [modelInstanceTree, setModelInstanceTree] = useState<ModelInstanceModelNode[]>([]);
  const [openRootMenuKey, setOpenRootMenuKey] = useState<string | null>(null);
  const [renameDialog, setRenameDialog] = useState<RenameDialogState | null>(null);
  const [renameMessage, setRenameMessage] = useState("");
  const [isRenamingRoot, setIsRenamingRoot] = useState(false);

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

  useEffect(() => {
    if (diagramMessage !== DIAGRAM_RENDERED_MESSAGE) {
      return;
    }
    const timeoutId = window.setTimeout(() => {
      setDiagramMessage("");
    }, 1800);
    return () => window.clearTimeout(timeoutId);
  }, [diagramMessage]);

  useEffect(() => {
    if (activeTab === "modelInstances" && apiBaseUrl) {
      void refreshModelInstances();
    }
  }, [activeTab, apiBaseUrl]);

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
      setModelMessage(nextModels.length === 0 ? "No models available" : `${nextModels.length} model${nextModels.length === 1 ? "" : "s"} available`);
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
      setDiagramMessage(DIAGRAM_EMPTY_MESSAGE);
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
      setDiagramMessage(DIAGRAM_RENDERED_MESSAGE);
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

  async function refreshModelInstances(baseUrl = apiBaseUrl) {
    if (!baseUrl) {
      setModelInstanceLoadState("error");
      setModelInstanceMessage("Backend URL is not configured");
      return;
    }

    setModelInstanceLoadState("loading");
    setModelInstanceMessage("Refreshing model instances...");

    try {
      const nextModels = await fetchModels(baseUrl);
      setModels(nextModels);
      setSelectedModelAzName((current) => {
        if (nextModels.some((model) => model.azName === current)) {
          return current;
        }
        return nextModels[0]?.azName ?? "";
      });
      const nextTree = await Promise.all(nextModels.map((model) => buildModelInstanceNode(baseUrl, model)));
      setModelInstanceTree(nextTree);
      setModelInstanceLoadState("ok");
      const modelErrorCount = nextTree.filter((model) => model.error).length;
      const entityErrorCount = nextTree.reduce(
        (total, model) => total + model.roots.reduce(
          (rootTotal, root) => rootTotal + root.entityGroups.filter((group) => group.error).length,
          0,
        ),
        0,
      );
      const entityGroupCount = nextTree.reduce(
        (total, model) => total + model.roots.reduce((rootTotal, root) => rootTotal + root.entityGroups.length, 0),
        0,
      );
      if (nextModels.length === 0) {
        setModelInstanceMessage("No models available");
      } else if (modelErrorCount > 0 || entityErrorCount > 0) {
        setModelInstanceMessage(`${nextModels.length} model${nextModels.length === 1 ? "" : "s"} loaded, ${modelErrorCount + entityErrorCount} instance detail issue${modelErrorCount + entityErrorCount === 1 ? "" : "s"}`);
      } else {
        setModelInstanceMessage(`${nextModels.length} model${nextModels.length === 1 ? "" : "s"}, ${entityGroupCount} entity group${entityGroupCount === 1 ? "" : "s"}`);
      }
    } catch (error) {
      setModelInstanceLoadState("error");
      setModelInstanceMessage(error instanceof Error ? error.message : "Model instance refresh failed");
    }
  }

  async function buildModelInstanceNode(apiBaseUrl: string, model: ModelSummary): Promise<ModelInstanceModelNode> {
    try {
      const apiDescription = await fetchModelInstanceApi(apiBaseUrl, model.azName);
      const roots = await fetchModelInstanceRoots(apiBaseUrl, model.azName);
      const rootNodes = await Promise.all(roots.map(async (root) => ({
        instanceRootId: root.instanceRootId,
        visName: root.visName,
        entityGroups: await Promise.all(apiDescription.entities.map((entity) => buildEntityInstanceGroup(apiBaseUrl, model.azName, root.instanceRootId, entity))),
      })));
      const hasEntityErrors = rootNodes.some((root) => root.entityGroups.some((group) => group.error));

      return {
        modelAzName: model.azName,
        modelVisName: model.visName,
        roots: rootNodes,
        error: hasEntityErrors ? "Entity counts unavailable" : undefined,
      };
    } catch (error) {
      return {
        modelAzName: model.azName,
        modelVisName: model.visName,
        roots: [],
        error: error instanceof Error ? error.message : "Model instance details unavailable",
      };
    }
  }

  function openRenameDialog(modelAzName: string, instanceRootId: string, currentName: string) {
    setOpenRootMenuKey(null);
    setRenameMessage("");
    setRenameDialog({
      modelAzName,
      instanceRootId,
      nextName: currentName,
    });
  }

  async function submitRootRename(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!apiBaseUrl || renameDialog === null) {
      return;
    }

    const nextName = renameDialog.nextName.trim();
    if (!nextName) {
      setRenameMessage("Name is required");
      return;
    }

    setIsRenamingRoot(true);
    setRenameMessage("");
    try {
      const renamed = await renameModelInstanceRoot(apiBaseUrl, renameDialog.modelAzName, renameDialog.instanceRootId, nextName);
      setModelInstanceTree((current) => current.map((model) => {
        if (model.modelAzName !== renamed.modelAzName) {
          return model;
        }
        return {
          ...model,
          roots: model.roots.map((root) => root.instanceRootId === renamed.instanceRootId
            ? {
                ...root,
                visName: renamed.visName,
              }
            : root),
        };
      }));
      setRenameDialog(null);
      setModelInstanceMessage(`Renamed model instance root to ${rootResponseDisplayName(renamed)}`);
    } catch (error) {
      setRenameMessage(error instanceof Error ? error.message : "Rename failed");
    } finally {
      setIsRenamingRoot(false);
    }
  }

  async function buildEntityInstanceGroup(apiBaseUrl: string, modelAzName: string, instanceRootId: string, entity: EntityDescription): Promise<EntityInstanceGroup> {
    try {
      return {
        entityAzName: entity.azName,
        entityVisName: entity.visName,
        count: await fetchEntityInstanceCount(apiBaseUrl, modelAzName, instanceRootId, entity.azName),
      };
    } catch (error) {
      return {
        entityAzName: entity.azName,
        entityVisName: entity.visName,
        error: error instanceof Error ? error.message : "Count unavailable",
      };
    }
  }

  async function toggleModelConnection() {
    if (modelConnectionState === "connected" || modelConnectionState === "connecting") {
      eventAdapterRef.current.disconnect();
      setModelConnectionState("disconnected");
      return;
    }

    if (!apiBaseUrl) {
      setModelConnectionState("error");
      setDiagramMessage("Backend URL is not configured");
      return;
    }
    if (!selectedModelAzName) {
      setModelConnectionState("error");
      setDiagramMessage("Select model before connecting");
      return;
    }

    setModelConnectionState("connecting");
    await renderSelectedModel(selectedModelAzName);

    eventAdapterRef.current.connect(apiBaseUrl, {
      onOpen: () => {
        setModelConnectionState("connected");
        window.sessionStorage.setItem(CONNECTED_MODEL_STORAGE_KEY, selectedModelAzName);
      },
      onClose: () => {
        setModelConnectionState("disconnected");
        window.sessionStorage.removeItem(CONNECTED_MODEL_STORAGE_KEY);
      },
      onError: (message) => {
        setModelConnectionState("error");
        setDiagramMessage(message);
      },
      onModelChanged: (modelAzName) => {
        void refreshModels();
        if (modelAzName.toLowerCase() === selectedModelAzNameRef.current.toLowerCase()) {
          void renderSelectedModel(modelAzName);
        }
      },
    });
  }

  const footerDiagramMessage = diagramMessage === DIAGRAM_EMPTY_MESSAGE ? "" : diagramMessage;
  const showDiagramPlaceholder = !diagramHasContent && diagramMessage === DIAGRAM_EMPTY_MESSAGE;
  const isModelsTab = activeTab === "models";
  const isConsolePaneVisible = isModelsTab && isConsolePaneOpen;
  const workspaceStyle = {
    "--console-pane-height": `${consolePaneHeight}px`,
  } as CSSProperties;

  function resizeConsolePane(event: PointerEvent<HTMLButtonElement>) {
    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    const nextHeight = clampConsolePaneHeight(window.innerHeight - event.clientY);
    setConsolePaneHeight(nextHeight);
    window.localStorage.setItem(CONSOLE_PANE_HEIGHT_STORAGE_KEY, String(nextHeight));
  }

  return (
    <main
      className={isConsolePaneVisible ? "workspace-shell workspace-shell-console-open" : "workspace-shell"}
      style={workspaceStyle}
    >
      <section className="app-shell">
        <nav className="workspace-tabs" aria-label="Vedenemo workspace tabs">
          <button
            type="button"
            className={activeTab === "models" ? "workspace-tab workspace-tab-active" : "workspace-tab"}
            onClick={() => setActiveTab("models")}
            aria-pressed={activeTab === "models"}
          >
            Models
          </button>
          <button
            type="button"
            className={activeTab === "modelInstances" ? "workspace-tab workspace-tab-active" : "workspace-tab"}
            onClick={() => setActiveTab("modelInstances")}
            aria-pressed={activeTab === "modelInstances"}
          >
            Model instances
          </button>
        </nav>
        <section className="card">
          {isModelsTab ? (
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
              <div className="diagram-viewport" aria-label="PlantUML class diagram">
                <div id={PLANTUML_TARGET_ID} className="diagram-svg" />
                {showDiagramPlaceholder && (
                  <div className="diagram-placeholder">{DIAGRAM_EMPTY_MESSAGE}</div>
                )}
              </div>
              {footerDiagramMessage && <span className="diagram-status">{footerDiagramMessage}</span>}
            </div>
          ) : (
            <div className="model-instances-panel">
              <div className="model-instances-toolbar">
                <button
                  type="button"
                  onClick={() => void refreshModelInstances()}
                  disabled={modelInstanceLoadState === "loading"}
                >
                  Refresh model instances
                </button>
                <span className={`model-status model-status-${modelInstanceLoadState}`}>{modelInstanceMessage}</span>
              </div>
              <div className="model-instance-tree" aria-label="Model instance tree">
                {modelInstanceTree.length === 0 ? (
                  <div className="model-instance-empty">No model instances available</div>
                ) : (
                  modelInstanceTree.map((model) => (
                    <details key={model.modelAzName} open className="tree-node tree-node-model">
                      <summary>{model.modelVisName} ({model.modelAzName})</summary>
                      <div className="tree-children">
                        {model.error ? (
                          <div className="tree-empty">Model instance details unavailable: {model.error}</div>
                        ) : model.roots.length === 0 ? (
                          <div className="tree-empty">No model instances loaded</div>
                        ) : model.roots.map((root) => {
                          const displayName = rootDisplayName(root);
                          const rootMenuKey = `${model.modelAzName}:${root.instanceRootId}`;
                          return (
                            <details key={`${model.modelAzName}-${root.instanceRootId}`} open className="tree-node tree-node-root">
                              <summary>
                                <span title={root.instanceRootId}>{displayName}</span>
                                <span className="tree-node-actions">
                                  <button
                                    type="button"
                                    className="tree-menu-button"
                                    aria-haspopup="menu"
                                    aria-expanded={openRootMenuKey === rootMenuKey}
                                    onClick={(event) => {
                                      event.preventDefault();
                                      setOpenRootMenuKey((current) => current === rootMenuKey ? null : rootMenuKey);
                                    }}
                                  >
                                    ...
                                  </button>
                                  {openRootMenuKey === rootMenuKey && (
                                    <span className="tree-menu" role="menu">
                                      <button
                                        type="button"
                                        role="menuitem"
                                        onClick={(event) => {
                                          event.preventDefault();
                                          openRenameDialog(model.modelAzName, root.instanceRootId, root.visName ?? "");
                                        }}
                                      >
                                        Rename...
                                      </button>
                                    </span>
                                  )}
                                </span>
                              </summary>
                              <ul className="tree-children tree-entity-groups">
                                {root.entityGroups.length === 0 ? (
                                  <li className="tree-empty">No entity types</li>
                                ) : (
                                  root.entityGroups.map((group) => (
                                    <li key={`${model.modelAzName}-${root.instanceRootId}-${group.entityAzName}`}>
                                      {group.error ? `${group.entityVisName} (?) - ${group.error}` : `${group.entityVisName} (${group.count})`}
                                    </li>
                                  ))
                                )}
                              </ul>
                            </details>
                          );
                        })}
                      </div>
                    </details>
                  ))
                )}
              </div>
              {renameDialog !== null && (
                <div className="dialog-backdrop" role="presentation">
                  <form
                    className="rename-dialog"
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby="model-instance-root-dialog-title"
                    onSubmit={(event) => void submitRootRename(event)}
                  >
                    <h2 id="model-instance-root-dialog-title">Rename model instance root</h2>
                    <label htmlFor="model-instance-root-name">Name</label>
                    <input
                      id="model-instance-root-name"
                      value={renameDialog.nextName}
                      maxLength={120}
                      autoFocus
                      onChange={(event) => setRenameDialog({
                        ...renameDialog,
                        nextName: event.target.value,
                      })}
                    />
                    {renameMessage && <span className="dialog-error">{renameMessage}</span>}
                    <div className="dialog-actions">
                      <button
                        type="button"
                        className="dialog-secondary"
                        onClick={() => {
                          setRenameDialog(null);
                          setRenameMessage("");
                        }}
                        disabled={isRenamingRoot}
                      >
                        Cancel
                      </button>
                      <button type="submit" disabled={isRenamingRoot}>
                        Rename
                      </button>
                    </div>
                  </form>
                </div>
              )}
            </div>
          )}
        </section>
      </section>
      {isConsolePaneVisible && (
        <section className="console-pane" aria-label="Vedenemo console pane">
          <button
            type="button"
            className="console-resize-handle"
            onPointerDown={resizeConsolePane}
            onPointerMove={(event) => {
              if (event.currentTarget.hasPointerCapture(event.pointerId)) {
                resizeConsolePane(event);
              }
            }}
            aria-label="Resize console pane"
            title="Resize console pane"
          />
          <ConsolePanel mode="pane" />
        </section>
      )}
      {isModelsTab && (
        <button
          type="button"
          className="console-toggle"
          onClick={() => setIsConsolePaneOpen((current) => !current)}
          aria-label={isConsolePaneOpen ? "Hide console pane" : "Show console pane"}
          title={isConsolePaneOpen ? "Hide console pane" : "Show console pane"}
        >
          {isConsolePaneOpen ? "⌄" : "⌃"}
        </button>
      )}
    </main>
  );
}
