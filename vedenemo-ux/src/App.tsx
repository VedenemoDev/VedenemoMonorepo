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
type QueryOperator = "=" | "<" | ">" | "contains";

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
  attributes: AttributeDescription[];
};

type ApiDescriptionResponse = {
  modelAzName: string;
  modelVisName: string;
  entities: EntityDescription[];
  associations?: AssociationDescription[];
};

type AttributeDescription = {
  azName: string;
  visName: string;
  dataType: string;
};

type AssociationDescription = {
  azName: string;
  visName: string;
  kind: string;
  sourceEntityAzName: string;
  targetEntityAzName: string;
  sourceRoleName?: string | null;
  targetRoleName?: string | null;
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

type EntityInstanceResponse = {
  id: string;
  modelAzName: string;
  modelVersion: string;
  entityAzName: string;
  values: Record<string, unknown>;
};

type AssociationLinkResponse = {
  id: string;
  modelAzName: string;
  associationAzName: string;
  sourceInstanceId: string;
  targetInstanceId: string;
};

type QueryComparisonRequest = {
  attributeAzName: string;
  operator: QueryOperator;
  value: string | number;
};

type QueryRelationshipRequest = {
  associationAzName: string;
  direction: RelationshipDirection;
  entityAzName: string;
  where: {
    comparisons: QueryComparisonRequest[];
  };
};

type QueryRequest = {
  where?: {
    comparisons: QueryComparisonRequest[];
  };
  relationships?: QueryRelationshipRequest[];
};

type RelationshipDirection = "outgoing" | "incoming";

type TraversalOption = {
  association: AssociationDescription;
  direction: RelationshipDirection;
  relatedEntity: EntityDescription;
};

type AssociationMatchContext = {
  associationLabel: string;
  criterionLabel: string;
  relatedEntityLabel: string;
  relatedInstanceId: string;
  matchedValueLabel?: string;
};

type EditorFormValues = Record<string, string>;

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

async function fetchModelInstanceRoot(apiBaseUrl: string, modelAzName: string, instanceRootId: string): Promise<ModelInstanceRootResponse> {
  const response = await fetch(`${apiBaseUrl}/data/${encodeURIComponent(modelAzName)}/roots/${encodeURIComponent(instanceRootId)}`, {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  return response.json() as Promise<ModelInstanceRootResponse>;
}

async function readErrorMessage(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { error?: string };
    return body.error ? `${body.error}` : `HTTP ${response.status}`;
  } catch {
    return `HTTP ${response.status}`;
  }
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

async function queryEntityInstances(
  apiBaseUrl: string,
  modelAzName: string,
  instanceRootId: string,
  entityAzName: string,
  request: QueryRequest,
): Promise<EntityInstanceResponse[]> {
  const response = await fetch(`${apiBaseUrl}/data/${encodeURIComponent(modelAzName)}/roots/${encodeURIComponent(instanceRootId)}/${encodeURIComponent(entityAzName)}/_query`, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  return response.json() as Promise<EntityInstanceResponse[]>;
}

async function createEntityInstance(
  apiBaseUrl: string,
  modelAzName: string,
  instanceRootId: string,
  entityAzName: string,
  values: Record<string, unknown>,
): Promise<EntityInstanceResponse> {
  const response = await fetch(`${apiBaseUrl}/data/${encodeURIComponent(modelAzName)}/roots/${encodeURIComponent(instanceRootId)}/${encodeURIComponent(entityAzName)}`, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify(values),
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }

  return response.json() as Promise<EntityInstanceResponse>;
}

async function updateEntityInstance(
  apiBaseUrl: string,
  modelAzName: string,
  instanceRootId: string,
  entityAzName: string,
  instanceId: string,
  values: Record<string, unknown>,
): Promise<EntityInstanceResponse> {
  const response = await fetch(`${apiBaseUrl}/data/${encodeURIComponent(modelAzName)}/roots/${encodeURIComponent(instanceRootId)}/${encodeURIComponent(entityAzName)}/${encodeURIComponent(instanceId)}`, {
    method: "PUT",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify(values),
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }

  return response.json() as Promise<EntityInstanceResponse>;
}

async function fetchAssociationLinks(
  apiBaseUrl: string,
  modelAzName: string,
  instanceRootId: string,
  associationAzName: string,
): Promise<AssociationLinkResponse[]> {
  const response = await fetch(`${apiBaseUrl}/data/${encodeURIComponent(modelAzName)}/roots/${encodeURIComponent(instanceRootId)}/_links/${encodeURIComponent(associationAzName)}`, {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  return response.json() as Promise<AssociationLinkResponse[]>;
}

async function fetchEntityInstance(
  apiBaseUrl: string,
  modelAzName: string,
  instanceRootId: string,
  entityAzName: string,
  instanceId: string,
): Promise<EntityInstanceResponse> {
  const response = await fetch(`${apiBaseUrl}/data/${encodeURIComponent(modelAzName)}/roots/${encodeURIComponent(instanceRootId)}/${encodeURIComponent(entityAzName)}/${encodeURIComponent(instanceId)}`, {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  return response.json() as Promise<EntityInstanceResponse>;
}

function readConnectedModelAzName(): string {
  return new URLSearchParams(window.location.search).get("connectedModelAzName") ?? "";
}

function readQueryParam(name: string): string {
  return new URLSearchParams(window.location.search).get(name) ?? "";
}

function readInitialActiveTab(): ActiveTab {
  return readQueryParam("tab") === "modelInstances" ? "modelInstances" : "models";
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

function formatInstanceValue(value: unknown): string {
  if (value === null || value === undefined) {
    return "";
  }
  if (typeof value === "string") {
    return value;
  }
  if (typeof value === "number" || typeof value === "boolean" || typeof value === "bigint") {
    return String(value);
  }
  return JSON.stringify(value);
}

function queryOperatorsFor(attribute: AttributeDescription | null): QueryOperator[] {
  if (attribute === null) {
    return ["="];
  }
  if (attribute.dataType === "NUMERIC") {
    return ["=", "<", ">"];
  }
  return ["=", "contains"];
}

function findEntity(entities: EntityDescription[], entityAzName: string): EntityDescription | null {
  return entities.find((entity) => sameAzName(entity.azName, entityAzName)) ?? null;
}

function sameAzName(left: string, right: string): boolean {
  return left.toLocaleLowerCase() === right.toLocaleLowerCase();
}

function traversalOptionsFor(entity: EntityDescription | null, apiDescription: ApiDescriptionResponse | null): TraversalOption[] {
  if (entity === null || apiDescription === null) {
    return [];
  }

  return (apiDescription.associations ?? []).flatMap((association) => {
    const options: TraversalOption[] = [];
    if (sameAzName(association.sourceEntityAzName, entity.azName)) {
      const relatedEntity = findEntity(apiDescription.entities, association.targetEntityAzName);
      if (relatedEntity !== null) {
        options.push({ association, direction: "outgoing", relatedEntity });
      }
    }
    if (sameAzName(association.targetEntityAzName, entity.azName)) {
      const relatedEntity = findEntity(apiDescription.entities, association.sourceEntityAzName);
      if (relatedEntity !== null) {
        options.push({ association, direction: "incoming", relatedEntity });
      }
    }
    return options;
  });
}

function traversalOptionValue(option: TraversalOption): string {
  return `${option.association.azName}::${option.direction}::${option.relatedEntity.azName}`;
}

function traversalLabel(option: TraversalOption): string {
  const association = option.association;
  const directionLabel = option.direction === "outgoing"
    ? `${association.sourceEntityAzName} -> ${association.targetEntityAzName}`
    : `${association.targetEntityAzName} -> ${association.sourceEntityAzName}`;
  const roles = [association.sourceRoleName, association.targetRoleName]
    .filter((role): role is string => typeof role === "string" && role.trim().length > 0)
    .join(" / ");
  const roleLabel = roles ? `, ${roles}` : "";
  return `${association.visName} (${association.azName}, ${association.kind}, ${directionLabel}${roleLabel})`;
}

function parseCriterionValue(attribute: AttributeDescription, rawValue: string): string | number {
  const trimmedValue = rawValue.trim();
  if (attribute.dataType !== "NUMERIC") {
    return trimmedValue;
  }
  return Number(trimmedValue);
}

function editorUrl(modelAzName: string, instanceRootId: string, entityAzName?: string, instanceId?: string): string {
  const params = new URLSearchParams({
    modelAzName,
    instanceRootId,
  });
  if (entityAzName) {
    params.set("entityAzName", entityAzName);
  }
  if (instanceId) {
    params.set("instanceId", instanceId);
  }
  return `/editor?${params.toString()}`;
}

function emptyEditorValues(entity: EntityDescription | null): EditorFormValues {
  if (entity === null) {
    return {};
  }
  return Object.fromEntries(entity.attributes.map((attribute) => [attribute.azName, ""]));
}

function formValuesFromInstance(entity: EntityDescription, instance: EntityInstanceResponse): EditorFormValues {
  return Object.fromEntries(entity.attributes.map((attribute) => [
    attribute.azName,
    formatInstanceValue(instance.values[attribute.azName]),
  ]));
}

function parseEditorFormValues(entity: EntityDescription, formValues: EditorFormValues): Record<string, unknown> {
  const values: Record<string, unknown> = {};
  for (const attribute of entity.attributes) {
    const rawValue = formValues[attribute.azName] ?? "";
    if (!rawValue.trim()) {
      throw new Error(`${attribute.visName} is required`);
    }
    if (attribute.dataType === "NUMERIC") {
      const numericValue = Number(rawValue);
      if (!Number.isFinite(numericValue)) {
        throw new Error(`${attribute.visName} must be a valid number`);
      }
      values[attribute.azName] = numericValue;
    } else {
      values[attribute.azName] = rawValue.trim();
    }
  }
  return values;
}

function relatedInstanceIdForLink(resultId: string, link: AssociationLinkResponse, direction: RelationshipDirection): string | null {
  if (direction === "outgoing") {
    return link.sourceInstanceId === resultId ? link.targetInstanceId : null;
  }
  return link.targetInstanceId === resultId ? link.sourceInstanceId : null;
}

function matchesQueryComparison(value: unknown, comparison: QueryComparisonRequest): boolean {
  if (value === null || value === undefined) {
    return false;
  }
  if (comparison.operator === "contains") {
    return typeof value === "string" && typeof comparison.value === "string" && value.includes(comparison.value);
  }
  if (comparison.operator === "<") {
    return typeof value === "number" && typeof comparison.value === "number" && value < comparison.value;
  }
  if (comparison.operator === ">") {
    return typeof value === "number" && typeof comparison.value === "number" && value > comparison.value;
  }
  return formatInstanceValue(value) === formatInstanceValue(comparison.value);
}

function defaultEntityDisplayAttribute(entity: EntityDescription): AttributeDescription | null {
  return entity.attributes[0] ?? null;
}

function entityInstanceLabel(entity: EntityDescription, instance: EntityInstanceResponse): string {
  const displayAttribute = defaultEntityDisplayAttribute(entity);
  const displayValue = displayAttribute === null ? "" : formatInstanceValue(instance.values[displayAttribute.azName]);
  return displayValue ? `${entity.visName}: ${displayValue}` : `${entity.visName}: ${instance.id}`;
}

function relationshipCriterionLabel(relationship: QueryRelationshipRequest, relatedEntity: EntityDescription): string {
  const comparison = relationship.where.comparisons[0] ?? null;
  if (comparison === null) {
    return `${relatedEntity.visName} exists`;
  }
  const attribute = relatedEntity.attributes.find((candidate) => candidate.azName === comparison.attributeAzName);
  return `${attribute?.visName ?? comparison.attributeAzName} ${comparison.operator} ${formatInstanceValue(comparison.value)}`;
}

async function buildAssociationMatchContexts(
  apiBaseUrl: string,
  modelAzName: string,
  instanceRootId: string,
  results: EntityInstanceResponse[],
  traversal: TraversalOption,
  relationship: QueryRelationshipRequest,
): Promise<Record<string, AssociationMatchContext[]>> {
  if (results.length === 0) {
    return {};
  }

  const resultIds = new Set(results.map((result) => result.id));
  const links = await fetchAssociationLinks(apiBaseUrl, modelAzName, instanceRootId, traversal.association.azName);
  const relatedIdsByResultId = new Map<string, Set<string>>();
  for (const link of links) {
    for (const resultId of resultIds) {
      const relatedId = relatedInstanceIdForLink(resultId, link, traversal.direction);
      if (relatedId !== null) {
        const relatedIds = relatedIdsByResultId.get(resultId) ?? new Set<string>();
        relatedIds.add(relatedId);
        relatedIdsByResultId.set(resultId, relatedIds);
      }
    }
  }

  const relatedInstancesById = new Map<string, EntityInstanceResponse>();
  const uniqueRelatedIds = [...new Set([...relatedIdsByResultId.values()].flatMap((relatedIds) => [...relatedIds]))];
  await Promise.all(uniqueRelatedIds.map(async (relatedId) => {
    const relatedInstance = await fetchEntityInstance(apiBaseUrl, modelAzName, instanceRootId, traversal.relatedEntity.azName, relatedId);
    relatedInstancesById.set(relatedId, relatedInstance);
  }));

  const contextsByResultId: Record<string, AssociationMatchContext[]> = {};
  for (const result of results) {
    const relatedIds = relatedIdsByResultId.get(result.id) ?? new Set<string>();
    const contexts = [...relatedIds]
      .map((relatedId) => relatedInstancesById.get(relatedId) ?? null)
      .filter((instance): instance is EntityInstanceResponse => instance !== null)
      .filter((instance) => relationship.where.comparisons.every((comparison) => matchesQueryComparison(instance.values[comparison.attributeAzName], comparison)))
      .map((instance) => {
        const comparison = relationship.where.comparisons[0] ?? null;
        return {
          associationLabel: traversalLabel(traversal),
          criterionLabel: relationshipCriterionLabel(relationship, traversal.relatedEntity),
          relatedEntityLabel: entityInstanceLabel(traversal.relatedEntity, instance),
          relatedInstanceId: instance.id,
          matchedValueLabel: comparison === null ? undefined : formatInstanceValue(instance.values[comparison.attributeAzName]),
        };
      });
    if (contexts.length > 0) {
      contextsByResultId[result.id] = contexts;
    }
  }
  return contextsByResultId;
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

function EditorPage() {
  const initialModelAzName = readQueryParam("modelAzName");
  const initialRootId = readQueryParam("instanceRootId");
  const initialEntityAzName = readQueryParam("entityAzName");
  const initialInstanceId = readQueryParam("instanceId");
  const [apiBaseUrl, setApiBaseUrl] = useState("");
  const [models, setModels] = useState<ModelSummary[]>([]);
  const [roots, setRoots] = useState<ModelInstanceRootResponse[]>([]);
  const [apiDescription, setApiDescription] = useState<ApiDescriptionResponse | null>(null);
  const [selectedModelAzName, setSelectedModelAzName] = useState(initialModelAzName);
  const [selectedRootId, setSelectedRootId] = useState(initialRootId);
  const [selectedEntityAzName, setSelectedEntityAzName] = useState(initialEntityAzName);
  const [loadedInstanceId, setLoadedInstanceId] = useState(initialInstanceId);
  const [createCopy, setCreateCopy] = useState(false);
  const [formValues, setFormValues] = useState<EditorFormValues>({});
  const [status, setStatus] = useState<ModelInstanceLoadState>("loading");
  const [statusMessage, setStatusMessage] = useState("Loading editor...");
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function loadEditorConfig() {
      try {
        const config = await loadRuntimeConfig();
        const baseUrl = normalizeBaseUrl(config.apiBaseUrl ?? "");
        if (!baseUrl) {
          throw new Error("Backend URL is not configured");
        }
        const nextModels = await fetchModels(baseUrl);
        if (cancelled) {
          return;
        }
        const nextModelAzName = selectedModelAzName || nextModels[0]?.azName || "";
        setApiBaseUrl(baseUrl);
        setModels(nextModels);
        setSelectedModelAzName(nextModelAzName);
        if (!nextModelAzName) {
          setStatus("ok");
          setStatusMessage("No models available");
        }
      } catch (error) {
        if (!cancelled) {
          setStatus("error");
          setStatusMessage(error instanceof Error ? error.message : "Editor load failed");
        }
      }
    }

    void loadEditorConfig();

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function loadModelContext() {
      if (!apiBaseUrl || !selectedModelAzName) {
        return;
      }

      setStatus("loading");
      setStatusMessage("Loading model instance schema...");
      try {
        const [nextApiDescription, nextRoots] = await Promise.all([
          fetchModelInstanceApi(apiBaseUrl, selectedModelAzName),
          fetchModelInstanceRoots(apiBaseUrl, selectedModelAzName),
        ]);
        if (cancelled) {
          return;
        }
        const nextRootId = nextRoots.some((root) => root.instanceRootId === selectedRootId)
          ? selectedRootId
          : nextRoots[0]?.instanceRootId ?? "";
        const nextEntityAzName = nextApiDescription.entities.some((entity) => entity.azName === selectedEntityAzName)
          ? selectedEntityAzName
          : nextApiDescription.entities[0]?.azName ?? "";
        setApiDescription(nextApiDescription);
        setRoots(nextRoots);
        setSelectedRootId(nextRootId);
        setSelectedEntityAzName(nextEntityAzName);
        if (nextEntityAzName !== selectedEntityAzName) {
          setLoadedInstanceId("");
          setCreateCopy(false);
        }
        const nextEntity = nextApiDescription.entities.find((entity) => entity.azName === nextEntityAzName) ?? null;
        setFormValues(emptyEditorValues(nextEntity));
        setStatus("ok");
        if (nextRoots.length === 0) {
          setStatusMessage("No model instance roots available");
        } else if (nextApiDescription.entities.length === 0) {
          setStatusMessage("No entity types available");
        } else {
          setStatusMessage("Ready");
        }
      } catch (error) {
        if (!cancelled) {
          setStatus("error");
          setStatusMessage(error instanceof Error ? error.message : "Model instance schema load failed");
        }
      }
    }

    void loadModelContext();

    return () => {
      cancelled = true;
    };
  }, [apiBaseUrl, selectedModelAzName]);

  const selectedEntity = apiDescription?.entities.find((entity) => entity.azName === selectedEntityAzName) ?? null;
  const selectedRoot = roots.find((root) => root.instanceRootId === selectedRootId) ?? null;
  const isEditMode = Boolean(loadedInstanceId);
  const willCreate = !isEditMode || createCopy;

  useEffect(() => {
    let cancelled = false;

    async function loadInstanceForEdit() {
      if (!apiBaseUrl || !selectedModelAzName || !selectedRootId || selectedEntity === null || !loadedInstanceId) {
        return;
      }

      setStatus("loading");
      setStatusMessage("Loading entity instance...");
      try {
        const instance = await fetchEntityInstance(apiBaseUrl, selectedModelAzName, selectedRootId, selectedEntity.azName, loadedInstanceId);
        if (cancelled) {
          return;
        }
        setFormValues(formValuesFromInstance(selectedEntity, instance));
        setStatus("ok");
        setStatusMessage("Ready");
      } catch (error) {
        if (!cancelled) {
          setStatus("error");
          setStatusMessage(error instanceof Error ? error.message : "Entity instance load failed");
        }
      }
    }

    void loadInstanceForEdit();

    return () => {
      cancelled = true;
    };
  }, [apiBaseUrl, selectedModelAzName, selectedRootId, selectedEntityAzName, loadedInstanceId]);

  function selectModel(nextModelAzName: string) {
    setSelectedModelAzName(nextModelAzName);
    setSelectedRootId("");
    setSelectedEntityAzName("");
    setLoadedInstanceId("");
    setCreateCopy(false);
    window.history.replaceState(null, "", "/editor");
  }

  function selectEntity(nextEntityAzName: string) {
    const nextEntity = apiDescription?.entities.find((entity) => entity.azName === nextEntityAzName) ?? null;
    setSelectedEntityAzName(nextEntityAzName);
    setLoadedInstanceId("");
    setCreateCopy(false);
    setFormValues(emptyEditorValues(nextEntity));
    setStatusMessage(nextEntity === null ? "Select an entity type" : "Ready");
  }

  async function submitEditor(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!apiBaseUrl || !selectedModelAzName || !selectedRootId || selectedEntity === null) {
      setStatus("error");
      setStatusMessage("Select model, root, and entity type");
      return;
    }

    let values: Record<string, unknown>;
    try {
      values = parseEditorFormValues(selectedEntity, formValues);
    } catch (error) {
      setStatus("error");
      setStatusMessage(error instanceof Error ? error.message : "Invalid editor values");
      return;
    }

    setIsSaving(true);
    setStatus("loading");
    setStatusMessage(willCreate ? "Creating entity instance..." : "Saving entity instance...");
    try {
      const saved = willCreate
        ? await createEntityInstance(apiBaseUrl, selectedModelAzName, selectedRootId, selectedEntity.azName, values)
        : await updateEntityInstance(apiBaseUrl, selectedModelAzName, selectedRootId, selectedEntity.azName, loadedInstanceId, values);
      setLoadedInstanceId(saved.id);
      setCreateCopy(false);
      setFormValues(formValuesFromInstance(selectedEntity, saved));
      window.history.replaceState(null, "", editorUrl(selectedModelAzName, selectedRootId, selectedEntity.azName, saved.id));
      setStatus("ok");
      setStatusMessage(willCreate ? `Created ${selectedEntity.visName}` : `Saved ${selectedEntity.visName}`);
    } catch (error) {
      setStatus("error");
      setStatusMessage(error instanceof Error ? error.message : "Save failed");
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <main className="editor-shell">
      <header className="editor-header">
        <div>
          <h1>Entity data editor</h1>
          <div className="query-console-targets">
            <span>{apiDescription?.modelVisName ?? (selectedModelAzName || "No model")}</span>
            <span>{selectedRoot === null ? (selectedRootId || "No root") : rootResponseDisplayName(selectedRoot)}</span>
            <span>{selectedEntity?.visName ?? (selectedEntityAzName || "No entity")}</span>
          </div>
        </div>
        <a className="secondary-link" href="/?tab=modelInstances">
          Model instances
        </a>
      </header>

      <section className="editor-surface">
        <form className="editor-form" onSubmit={(event) => void submitEditor(event)}>
          <div className="editor-context-grid">
            <div className="query-field">
              <label htmlFor="editor-model">Model</label>
              <select
                id="editor-model"
                value={selectedModelAzName}
                onChange={(event) => selectModel(event.target.value)}
                disabled={status === "loading" || models.length === 0}
              >
                {models.length === 0 ? (
                  <option value="">No models</option>
                ) : models.map((model) => (
                  <option key={model.azName} value={model.azName}>
                    {model.visName} ({model.azName})
                  </option>
                ))}
              </select>
            </div>
            <div className="query-field">
              <label htmlFor="editor-root">Model instance</label>
              <select
                id="editor-root"
                value={selectedRootId}
                onChange={(event) => {
                  setSelectedRootId(event.target.value);
                  setLoadedInstanceId("");
                  setCreateCopy(false);
                  setFormValues(emptyEditorValues(selectedEntity));
                }}
                disabled={status === "loading" || roots.length === 0}
              >
                {roots.length === 0 ? (
                  <option value="">No roots</option>
                ) : roots.map((root) => (
                  <option key={root.instanceRootId} value={root.instanceRootId}>
                    {rootResponseDisplayName(root)}
                  </option>
                ))}
              </select>
            </div>
            <div className="query-field">
              <label htmlFor="editor-entity">Entity type</label>
              <select
                id="editor-entity"
                value={selectedEntityAzName}
                onChange={(event) => selectEntity(event.target.value)}
                disabled={status === "loading" || apiDescription === null || apiDescription.entities.length === 0}
              >
                {apiDescription === null || apiDescription.entities.length === 0 ? (
                  <option value="">No entity types</option>
                ) : apiDescription.entities.map((entity) => (
                  <option key={entity.azName} value={entity.azName}>
                    {entity.visName} ({entity.azName})
                  </option>
                ))}
              </select>
            </div>
          </div>

          {isEditMode && (
            <div className="query-criterion-toggle editor-copy-toggle">
              <label htmlFor="editor-create-copy">
                <input
                  id="editor-create-copy"
                  type="checkbox"
                  checked={createCopy}
                  onChange={(event) => setCreateCopy(event.target.checked)}
                  disabled={status === "loading" || isSaving}
                />
                Create copy
              </label>
            </div>
          )}

          <div className="editor-fields">
            {selectedEntity === null || selectedEntity.attributes.length === 0 ? (
              <div className="tree-empty">No attributes</div>
            ) : selectedEntity.attributes.map((attribute) => (
              <div key={attribute.azName} className="query-field">
                <label htmlFor={`editor-${attribute.azName}`}>{attribute.visName}</label>
                {attribute.dataType === "DATA" ? (
                  <textarea
                    id={`editor-${attribute.azName}`}
                    value={formValues[attribute.azName] ?? ""}
                    onChange={(event) => setFormValues((current) => ({ ...current, [attribute.azName]: event.target.value }))}
                    disabled={status === "loading" || isSaving}
                  />
                ) : (
                  <input
                    id={`editor-${attribute.azName}`}
                    value={formValues[attribute.azName] ?? ""}
                    type={attribute.dataType === "NUMERIC" ? "number" : attribute.dataType === "URL" ? "url" : "text"}
                    step={attribute.dataType === "NUMERIC" ? "any" : undefined}
                    onChange={(event) => setFormValues((current) => ({ ...current, [attribute.azName]: event.target.value }))}
                    disabled={status === "loading" || isSaving}
                  />
                )}
              </div>
            ))}
          </div>

          <div className="editor-actions">
            <span className={`model-status model-status-${status}`}>{statusMessage}</span>
            <button type="submit" disabled={isSaving || status === "loading" || selectedEntity === null || !selectedRootId}>
              {willCreate ? "Create" : "Save"}
            </button>
          </div>
        </form>
      </section>
    </main>
  );
}

function QueryConsolePage() {
  const modelAzName = readQueryParam("modelAzName");
  const instanceRootId = readQueryParam("instanceRootId");
  const [apiBaseUrl, setApiBaseUrl] = useState("");
  const [apiDescription, setApiDescription] = useState<ApiDescriptionResponse | null>(null);
  const [root, setRoot] = useState<ModelInstanceRootResponse | null>(null);
  const [selectedEntityAzName, setSelectedEntityAzName] = useState("");
  const [selectedDisplayAttributeAzName, setSelectedDisplayAttributeAzName] = useState("");
  const [useDirectCriterion, setUseDirectCriterion] = useState(true);
  const [selectedAttributeAzName, setSelectedAttributeAzName] = useState("");
  const [selectedOperator, setSelectedOperator] = useState<QueryOperator>("=");
  const [criterionValue, setCriterionValue] = useState("");
  const [useRelationshipCriterion, setUseRelationshipCriterion] = useState(false);
  const [selectedTraversalValue, setSelectedTraversalValue] = useState("");
  const [selectedRelatedAttributeAzName, setSelectedRelatedAttributeAzName] = useState("");
  const [selectedRelationshipOperator, setSelectedRelationshipOperator] = useState<QueryOperator>("=");
  const [relationshipCriterionValue, setRelationshipCriterionValue] = useState("");
  const [results, setResults] = useState<EntityInstanceResponse[]>([]);
  const [associationMatchContexts, setAssociationMatchContexts] = useState<Record<string, AssociationMatchContext[]>>({});
  const [openResultMenuId, setOpenResultMenuId] = useState<string | null>(null);
  const [status, setStatus] = useState<ModelInstanceLoadState>("loading");
  const [statusMessage, setStatusMessage] = useState("Loading query console...");
  const [isQuerying, setIsQuerying] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function loadQueryConsole() {
      if (!modelAzName || !instanceRootId) {
        setStatus("error");
        setStatusMessage("Query console URL is missing modelAzName or instanceRootId");
        return;
      }

      try {
        const config = await loadRuntimeConfig();
        const baseUrl = normalizeBaseUrl(config.apiBaseUrl ?? "");
        if (!baseUrl) {
          throw new Error("Backend URL is not configured");
        }
        const [nextApiDescription, nextRoot] = await Promise.all([
          fetchModelInstanceApi(baseUrl, modelAzName),
          fetchModelInstanceRoot(baseUrl, modelAzName, instanceRootId),
        ]);
        if (cancelled) {
          return;
        }

        setApiBaseUrl(baseUrl);
        setApiDescription(nextApiDescription);
        setRoot(nextRoot);
        const firstEntity = nextApiDescription.entities[0];
        setSelectedEntityAzName(firstEntity?.azName ?? "");
        setSelectedDisplayAttributeAzName(firstEntity?.attributes[0]?.azName ?? "");
        setSelectedAttributeAzName(firstEntity?.attributes[0]?.azName ?? "");
        setSelectedOperator("=");
        const firstTraversal = traversalOptionsFor(firstEntity ?? null, nextApiDescription)[0] ?? null;
        setSelectedTraversalValue(firstTraversal === null ? "" : traversalOptionValue(firstTraversal));
        setSelectedRelatedAttributeAzName(firstTraversal?.relatedEntity.attributes[0]?.azName ?? "");
        setSelectedRelationshipOperator("=");
        setRelationshipCriterionValue(firstTraversal === null ? "" : "*");
        setStatus("ok");
        setStatusMessage(nextApiDescription.entities.length === 0 ? "No entity types available" : "Ready");
      } catch (error) {
        if (!cancelled) {
          setStatus("error");
          setStatusMessage(error instanceof Error ? error.message : "Query console load failed");
        }
      }
    }

    void loadQueryConsole();

    return () => {
      cancelled = true;
    };
  }, [modelAzName, instanceRootId]);

  const selectedEntity = apiDescription?.entities.find((entity) => entity.azName === selectedEntityAzName) ?? null;
  const selectedDisplayAttribute = selectedEntity?.attributes.find((attribute) => attribute.azName === selectedDisplayAttributeAzName) ?? selectedEntity?.attributes[0] ?? null;
  const selectedAttribute = selectedEntity?.attributes.find((attribute) => attribute.azName === selectedAttributeAzName) ?? null;
  const selectedOperators = queryOperatorsFor(selectedAttribute);
  const traversalOptions = traversalOptionsFor(selectedEntity, apiDescription);
  const selectedTraversal = traversalOptions.find((option) => traversalOptionValue(option) === selectedTraversalValue) ?? traversalOptions[0] ?? null;
  const selectedRelatedAttribute = selectedTraversal?.relatedEntity.attributes.find((attribute) => attribute.azName === selectedRelatedAttributeAzName) ?? selectedTraversal?.relatedEntity.attributes[0] ?? null;
  const selectedRelationshipOperators = queryOperatorsFor(selectedRelatedAttribute);
  const rootName = root === null ? instanceRootId : rootResponseDisplayName(root);

  function selectEntity(nextEntityAzName: string) {
    const nextEntity = apiDescription?.entities.find((entity) => entity.azName === nextEntityAzName) ?? null;
    const nextTraversal = traversalOptionsFor(nextEntity, apiDescription)[0] ?? null;
    setSelectedEntityAzName(nextEntityAzName);
    setSelectedDisplayAttributeAzName(nextEntity?.attributes[0]?.azName ?? "");
    setSelectedAttributeAzName(nextEntity?.attributes[0]?.azName ?? "");
    setSelectedOperator("=");
    setResults([]);
    setAssociationMatchContexts({});
    setCriterionValue("");
    setSelectedTraversalValue(nextTraversal === null ? "" : traversalOptionValue(nextTraversal));
    setSelectedRelatedAttributeAzName(nextTraversal?.relatedEntity.attributes[0]?.azName ?? "");
    setSelectedRelationshipOperator("=");
    setRelationshipCriterionValue(nextTraversal === null ? "" : "*");
    setOpenResultMenuId(null);
    setStatusMessage(nextEntity === null ? "Select an entity type" : "Ready");
  }

  async function submitQuery(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!apiBaseUrl || apiDescription === null || selectedEntity === null) {
      setStatus("error");
      setStatusMessage("Select an entity type");
      return;
    }
    if (!useDirectCriterion && !useRelationshipCriterion) {
      setStatus("error");
      setStatusMessage("Select at least one criterion");
      return;
    }

    const comparisons: QueryComparisonRequest[] = [];
    if (useDirectCriterion) {
      if (selectedAttribute === null) {
        setStatus("error");
        setStatusMessage("Select a direct attribute");
        return;
      }
      if (!criterionValue.trim()) {
        setStatus("error");
        setStatusMessage("Direct criterion value is required");
        return;
      }
      const queryValue = parseCriterionValue(selectedAttribute, criterionValue);
      if (selectedAttribute.dataType === "NUMERIC" && !Number.isFinite(queryValue)) {
        setStatus("error");
        setStatusMessage("Direct numeric criterion must be a valid number");
        return;
      }
      comparisons.push({
        attributeAzName: selectedAttribute.azName,
        operator: selectedOperator,
        value: queryValue,
      });
    }

    const relationships: QueryRelationshipRequest[] = [];
    if (useRelationshipCriterion) {
      if (selectedTraversal === null) {
        setStatus("error");
        setStatusMessage("Select an association criterion");
        return;
      }
      const trimmedRelationshipCriterionValue = relationshipCriterionValue.trim();
      if (!trimmedRelationshipCriterionValue) {
        setStatus("error");
        setStatusMessage("Association criterion value is required");
        return;
      }
      const relatedComparisons: QueryComparisonRequest[] = [];
      if (trimmedRelationshipCriterionValue !== "*") {
        if (selectedRelatedAttribute === null) {
          setStatus("error");
          setStatusMessage("Select a related attribute");
          return;
        }
        const relatedQueryValue = parseCriterionValue(selectedRelatedAttribute, relationshipCriterionValue);
        if (selectedRelatedAttribute.dataType === "NUMERIC" && !Number.isFinite(relatedQueryValue)) {
          setStatus("error");
          setStatusMessage("Association numeric criterion must be a valid number");
          return;
        }
        relatedComparisons.push({
          attributeAzName: selectedRelatedAttribute.azName,
          operator: selectedRelationshipOperator,
          value: relatedQueryValue,
        });
      }
      relationships.push({
        associationAzName: selectedTraversal.association.azName,
        direction: selectedTraversal.direction,
        entityAzName: selectedTraversal.relatedEntity.azName,
        where: {
          comparisons: relatedComparisons,
        },
      });
    }

    setIsQuerying(true);
    setStatus("loading");
    setStatusMessage("Running query...");
    try {
      const nextResults = await queryEntityInstances(
        apiBaseUrl,
        apiDescription.modelAzName,
        instanceRootId,
        selectedEntity.azName,
        {
          where: comparisons.length === 0 ? undefined : { comparisons },
          relationships: relationships.length === 0 ? undefined : relationships,
        },
      );
      const nextMatchContexts = relationships.length === 0 || selectedTraversal === null
        ? {}
        : await buildAssociationMatchContexts(
          apiBaseUrl,
          apiDescription.modelAzName,
          instanceRootId,
          nextResults,
          selectedTraversal,
          relationships[0],
        );
      setResults(nextResults);
      setAssociationMatchContexts(nextMatchContexts);
      setOpenResultMenuId(null);
      setStatus("ok");
      setStatusMessage(`${nextResults.length} result${nextResults.length === 1 ? "" : "s"}`);
    } catch (error) {
      setStatus("error");
      setStatusMessage(error instanceof Error ? error.message : "Query failed");
    } finally {
      setIsQuerying(false);
    }
  }

  return (
    <main className="query-console-shell">
      <header className="query-console-header">
        <div>
          <h1>Query Console</h1>
          <div className="query-console-targets">
            <span>{apiDescription?.modelVisName ?? modelAzName}</span>
            <span>{rootName}</span>
          </div>
        </div>
        <a className="secondary-link" href="/?tab=modelInstances">
          Model instances
        </a>
      </header>

      <section className="query-console-surface">
        <form className="query-form" onSubmit={(event) => void submitQuery(event)}>
          <div className="query-field">
            <label htmlFor="query-entity">Entity type</label>
            <select
              id="query-entity"
              value={selectedEntityAzName}
              onChange={(event) => selectEntity(event.target.value)}
              disabled={status === "loading" || apiDescription === null || apiDescription.entities.length === 0}
            >
              {apiDescription === null || apiDescription.entities.length === 0 ? (
                <option value="">No entity types</option>
              ) : (
                apiDescription.entities.map((entity) => (
                  <option key={entity.azName} value={entity.azName}>
                    {entity.visName} ({entity.azName})
                  </option>
                ))
              )}
            </select>
          </div>

          <div className="query-field">
            <label htmlFor="query-display-attribute">Display field</label>
            <select
              id="query-display-attribute"
              value={selectedDisplayAttributeAzName}
              onChange={(event) => setSelectedDisplayAttributeAzName(event.target.value)}
              disabled={status === "loading" || selectedEntity === null || selectedEntity.attributes.length === 0}
            >
              {selectedEntity === null || selectedEntity.attributes.length === 0 ? (
                <option value="">No attributes</option>
              ) : (
                selectedEntity.attributes.map((attribute) => (
                  <option key={attribute.azName} value={attribute.azName}>
                    {attribute.visName} ({attribute.azName})
                  </option>
                ))
              )}
            </select>
          </div>

          <div className="query-criterion-toggle">
            <label htmlFor="query-use-direct">
              <input
                id="query-use-direct"
                type="checkbox"
                checked={useDirectCriterion}
                onChange={(event) => {
                  setUseDirectCriterion(event.target.checked);
                  setResults([]);
                  setStatusMessage("Ready");
                }}
                disabled={status === "loading" || selectedEntity === null || selectedEntity.attributes.length === 0}
              />
              Direct criterion
            </label>
          </div>

          <div className="query-field">
            <label htmlFor="query-attribute">Attribute</label>
            <select
              id="query-attribute"
              value={selectedAttributeAzName}
              onChange={(event) => {
                setSelectedAttributeAzName(event.target.value);
                setSelectedOperator("=");
                setResults([]);
                setStatusMessage("Ready");
              }}
              disabled={status === "loading" || !useDirectCriterion || selectedEntity === null || selectedEntity.attributes.length === 0}
            >
              {selectedEntity === null || selectedEntity.attributes.length === 0 ? (
                <option value="">No attributes</option>
              ) : (
                selectedEntity.attributes.map((attribute) => (
                  <option key={attribute.azName} value={attribute.azName}>
                    {attribute.visName} ({attribute.azName})
                  </option>
                ))
              )}
            </select>
          </div>

          <div className="query-field query-field-operator">
            <label htmlFor="query-operator">Operator</label>
            <select
              id="query-operator"
              value={selectedOperator}
              onChange={(event) => setSelectedOperator(event.target.value as QueryOperator)}
              disabled={status === "loading" || !useDirectCriterion || selectedAttribute === null}
            >
              {selectedOperators.map((operator) => (
                <option key={operator} value={operator}>{operator}</option>
              ))}
            </select>
          </div>

          <div className="query-field">
            <label htmlFor="query-value">Value</label>
            <input
              id="query-value"
              value={criterionValue}
              type={selectedAttribute?.dataType === "NUMERIC" ? "number" : "text"}
              onChange={(event) => setCriterionValue(event.target.value)}
              disabled={status === "loading" || !useDirectCriterion || selectedAttribute === null}
            />
          </div>

          <div className="query-criterion-toggle">
            <label htmlFor="query-use-relationship">
              <input
                id="query-use-relationship"
                type="checkbox"
                checked={useRelationshipCriterion}
                onChange={(event) => {
                  setUseRelationshipCriterion(event.target.checked);
                  if (event.target.checked && selectedTraversal !== null && !relationshipCriterionValue.trim()) {
                    setRelationshipCriterionValue("*");
                  }
                  setResults([]);
                  setStatusMessage("Ready");
                }}
                disabled={status === "loading" || traversalOptions.length === 0}
              />
              Association criterion
            </label>
          </div>

          <div className="query-field query-field-wide">
            <label htmlFor="query-association">Association</label>
            <select
              id="query-association"
              value={selectedTraversal === null ? "" : traversalOptionValue(selectedTraversal)}
              onChange={(event) => {
                const nextTraversal = traversalOptions.find((option) => traversalOptionValue(option) === event.target.value) ?? null;
                setSelectedTraversalValue(event.target.value);
                setSelectedRelatedAttributeAzName(nextTraversal?.relatedEntity.attributes[0]?.azName ?? "");
                setSelectedRelationshipOperator("=");
                if (nextTraversal !== null && !relationshipCriterionValue.trim()) {
                  setRelationshipCriterionValue("*");
                }
                setResults([]);
                setStatusMessage("Ready");
              }}
              disabled={status === "loading" || !useRelationshipCriterion || traversalOptions.length === 0}
            >
              {traversalOptions.length === 0 ? (
                <option value="">No traversable associations</option>
              ) : (
                traversalOptions.map((option) => (
                  <option key={traversalOptionValue(option)} value={traversalOptionValue(option)}>
                    {traversalLabel(option)}
                  </option>
                ))
              )}
            </select>
          </div>

          <div className="query-field">
            <label htmlFor="query-related-attribute">Related attribute</label>
            <select
              id="query-related-attribute"
              value={selectedRelatedAttribute?.azName ?? ""}
              onChange={(event) => {
                setSelectedRelatedAttributeAzName(event.target.value);
                setSelectedRelationshipOperator("=");
                setResults([]);
                setStatusMessage("Ready");
              }}
              disabled={status === "loading" || !useRelationshipCriterion || selectedTraversal === null || selectedTraversal.relatedEntity.attributes.length === 0}
            >
              {selectedTraversal === null || selectedTraversal.relatedEntity.attributes.length === 0 ? (
                <option value="">No related attributes</option>
              ) : (
                selectedTraversal.relatedEntity.attributes.map((attribute) => (
                  <option key={attribute.azName} value={attribute.azName}>
                    {attribute.visName} ({attribute.azName})
                  </option>
                ))
              )}
            </select>
          </div>

          <div className="query-field query-field-operator">
            <label htmlFor="query-related-operator">Operator</label>
            <select
              id="query-related-operator"
              value={selectedRelationshipOperator}
              onChange={(event) => setSelectedRelationshipOperator(event.target.value as QueryOperator)}
              disabled={status === "loading" || !useRelationshipCriterion || selectedRelatedAttribute === null}
            >
              {selectedRelationshipOperators.map((operator) => (
                <option key={operator} value={operator}>{operator}</option>
              ))}
            </select>
          </div>

          <div className="query-field">
            <label htmlFor="query-related-value">Related value</label>
            <input
              id="query-related-value"
              value={relationshipCriterionValue}
              type="text"
              inputMode={selectedRelatedAttribute?.dataType === "NUMERIC" ? "decimal" : undefined}
              placeholder="*"
              onChange={(event) => setRelationshipCriterionValue(event.target.value)}
              disabled={status === "loading" || !useRelationshipCriterion || selectedTraversal === null}
            />
          </div>

          <button type="submit" disabled={isQuerying || status === "loading" || (!useDirectCriterion && !useRelationshipCriterion)}>
            Query
          </button>
        </form>

        <span className={`model-status model-status-${status}`}>{statusMessage}</span>

        <div className="query-results-tree" aria-label="Query results">
          {results.length === 0 ? (
            <div className="tree-empty">No query results</div>
          ) : (
            results.map((result) => (
              <details key={result.id} className="tree-node query-result-node">
                <summary>
                  <span>
                    {selectedEntity?.visName ?? result.entityAzName}: {formatInstanceValue(result.values[selectedDisplayAttribute?.azName ?? ""])}
                  </span>
                  <span className="tree-node-actions">
                    <button
                      type="button"
                      className="tree-menu-button"
                      aria-haspopup="menu"
                      aria-expanded={openResultMenuId === result.id}
                      onClick={(event) => {
                        event.preventDefault();
                        setOpenResultMenuId((current) => current === result.id ? null : result.id);
                      }}
                    >
                      ...
                    </button>
                    {openResultMenuId === result.id && (
                      <span className="tree-menu" role="menu">
                        <button
                          type="button"
                          role="menuitem"
                          onClick={(event) => {
                            event.preventDefault();
                            window.open(editorUrl(apiDescription?.modelAzName ?? modelAzName, instanceRootId, result.entityAzName, result.id), "_blank", "noopener,noreferrer");
                          }}
                        >
                          Editor...
                        </button>
                      </span>
                    )}
                  </span>
                </summary>
                <ul className="tree-children query-result-values">
                  {Object.entries(result.values).map(([attributeAzName, value]) => {
                    const attribute = selectedEntity?.attributes.find((candidate) => candidate.azName === attributeAzName);
                    return (
                      <li key={`${result.id}-${attributeAzName}`}>
                        <span>{attribute?.visName ?? attributeAzName}</span>
                        <span>{formatInstanceValue(value)}</span>
                      </li>
                    );
                  })}
                  {(associationMatchContexts[result.id] ?? []).map((context, index) => (
                    <li key={`${result.id}-association-match-${index}`} className="query-association-match">
                      <span>Matched association</span>
                      <span>
                        <strong>{context.associationLabel}</strong>
                        <span>{context.criterionLabel}</span>
                        <span>{context.relatedEntityLabel}</span>
                        {context.matchedValueLabel ? <span>Matched value: {context.matchedValueLabel}</span> : null}
                      </span>
                    </li>
                  ))}
                </ul>
              </details>
            ))
          )}
        </div>
      </section>
    </main>
  );
}

export function App() {
  if (window.location.pathname === "/console") {
    return <ConsolePage />;
  }
  if (window.location.pathname === "/editor") {
    return <EditorPage />;
  }
  if (window.location.pathname === "/queryConsole") {
    return <QueryConsolePage />;
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
  const [activeTab, setActiveTab] = useState<ActiveTab>(readInitialActiveTab);
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

  function openQueryConsole(modelAzName: string, instanceRootId: string) {
    setOpenRootMenuKey(null);
    const params = new URLSearchParams({
      modelAzName,
      instanceRootId,
    });
    window.open(`/queryConsole?${params.toString()}`, "_blank", "noopener,noreferrer");
  }

  function openEditor(modelAzName: string, instanceRootId: string) {
    setOpenRootMenuKey(null);
    window.open(editorUrl(modelAzName, instanceRootId), "_blank", "noopener,noreferrer");
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
                                      <button
                                        type="button"
                                        role="menuitem"
                                        onClick={(event) => {
                                          event.preventDefault();
                                          openQueryConsole(model.modelAzName, root.instanceRootId);
                                        }}
                                      >
                                        Query console...
                                      </button>
                                      <button
                                        type="button"
                                        role="menuitem"
                                        onClick={(event) => {
                                          event.preventDefault();
                                          openEditor(model.modelAzName, root.instanceRootId);
                                        }}
                                      >
                                        Editor...
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
