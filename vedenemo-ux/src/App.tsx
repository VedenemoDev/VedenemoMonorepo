import {
  type CSSProperties,
  type FormEvent,
  type KeyboardEvent,
  type PointerEvent,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import * as d3 from "d3";
import { ModelChangeEventAdapter } from "./adapters/ModelChangeEventAdapter";
import { PlantUmlModelAdapter } from "./adapters/PlantUmlModelAdapter";

type ModelLoadState = "idle" | "loading" | "ok" | "error";
type ModelConnectionState = "disconnected" | "connecting" | "connected" | "error";
type ConsoleStatus = "loading" | "ready" | "error";
type ActiveTab = "models" | "modelInstances";
type EditorTab = "entity" | "associations";
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
  operations: Record<string, string>;
  createBodyExample: Record<string, unknown>;
};

type ApiDescriptionResponse = {
  modelAzName: string;
  modelVisName: string;
  modelVersion?: string;
  valueSets?: ValueSetDescription[];
  entities: EntityDescription[];
  associations?: AssociationDescription[];
};

type AttributeDescription = {
  azName: string;
  visName: string;
  dataType: string;
  required?: boolean;
  valueSetAzName?: string | null;
};

type ValueSetDescription = {
  azName: string;
  dataType: string;
  entries: ValueSetEntryDescription[];
};

type ValueSetEntryDescription = {
  technicalValue: unknown;
  visName: string;
};

type AssociationDescription = {
  azName: string;
  visName: string;
  kind: string;
  sourceEntityAzName: string;
  targetEntityAzName: string;
  cardinality?: string | null;
  sourceRoleName?: string | null;
  targetRoleName?: string | null;
  sourceCardinality?: string | null;
  targetCardinality?: string | null;
  linkOperations?: Record<string, string>;
  createBodyExample?: Record<string, string>;
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

type TryItResult = {
  method: string;
  url: string;
  requestBody: string;
  statusCode?: number;
  responseBody: string;
  errorMessage?: string;
};

type VisualizationWizardStep = "chartType" | "binding" | "visualization";

type ChartEligibility = {
  selectable: boolean;
  reason?: string;
};

type ChartTypeDefinition = {
  id: string;
  name: string;
  summary: string;
  evaluateEligibility: (apiDescription: ApiDescriptionResponse) => ChartEligibility;
};

type TidyTreeBindingLevel = {
  entityAzName: string;
  labelTemplate: string;
  filter?: TidyTreeLevelFilter;
  traversal?: {
    associationAzName: string;
    direction: RelationshipDirection;
  };
};

type TidyTreeRootMode = "manual" | "entity";

type TidyTreeRootDirectCriterion = {
  attributeAzName: string;
  operator: QueryOperator;
  value: string;
};

type TidyTreeRootRelationshipCriterion = {
  traversalValue: string;
  relatedAttributeAzName: string;
  operator: QueryOperator;
  value: string;
};

type TidyTreeLevelFilter = {
  enabled: boolean;
  directCriteria: TidyTreeRootDirectCriterion[];
  relationshipCriteria: TidyTreeRootRelationshipCriterion[];
};

type TidyTreeRootSelection = {
  mode: TidyTreeRootMode;
  labelTemplate: string;
  directCriteria: TidyTreeRootDirectCriterion[];
  relationshipCriteria: TidyTreeRootRelationshipCriterion[];
};

type TidyTreeBinding = {
  rootLabel: string;
  rootSelection: TidyTreeRootSelection;
  levels: TidyTreeBindingLevel[];
};

type TidyTreeNode = {
  id: string;
  label: string;
  detail?: string;
  children: TidyTreeNode[];
};

type VisualizationDataState = {
  status: ModelInstanceLoadState;
  message: string;
  tree: TidyTreeNode | null;
  loadedAt?: string;
};

type RootMatchState = {
  status: ModelInstanceLoadState;
  message: string;
  count?: number;
  instance?: EntityInstanceResponse;
};

const PLANTUML_TARGET_ID = "plantuml-diagram";
const CONNECTED_MODEL_STORAGE_KEY = "vedenemo.connectedModelAzName";
const CONSOLE_PANE_HEIGHT_STORAGE_KEY = "vedenemo.consolePaneHeight";
const DIAGRAM_EMPTY_MESSAGE = "Select model and connect to show diagram.";
const DIAGRAM_RENDERED_MESSAGE = "Diagram rendered";
const DEFAULT_CONSOLE_PANE_HEIGHT = 360;
const MIN_CONSOLE_PANE_HEIGHT = 256;
const MAX_CONSOLE_PANE_VIEWPORT_RATIO = 0.75;
const TIDY_TREE_CHART_ID = "tidy-tree";
const RADIAL_TREE_CHART_ID = "radial-tree";
const TREE_OF_LIFE_CHART_ID = "tree-of-life";

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

async function fetchRootModelInstanceApi(apiBaseUrl: string, modelAzName: string, instanceRootId: string): Promise<ApiDescriptionResponse> {
  const response = await fetch(`${apiBaseUrl}/data/${encodeURIComponent(modelAzName)}/roots/${encodeURIComponent(instanceRootId)}/_api`, {
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

async function createAssociationLink(
  apiBaseUrl: string,
  modelAzName: string,
  instanceRootId: string,
  associationAzName: string,
  sourceInstanceId: string,
  targetInstanceId: string,
): Promise<AssociationLinkResponse> {
  const response = await fetch(`${apiBaseUrl}/data/${encodeURIComponent(modelAzName)}/roots/${encodeURIComponent(instanceRootId)}/_links/${encodeURIComponent(associationAzName)}`, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ sourceInstanceId, targetInstanceId }),
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }

  return response.json() as Promise<AssociationLinkResponse>;
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

const CHART_TYPES: ChartTypeDefinition[] = [
  {
    id: TIDY_TREE_CHART_ID,
    name: "Tidy tree",
    summary: "Hierarchical node-link tree for acyclic entity paths.",
    evaluateEligibility: evaluateTidyTreeEligibility,
  },
  {
    id: RADIAL_TREE_CHART_ID,
    name: "Radial tree",
    summary: "Radial node-link tree for the same hierarchical bindings.",
    evaluateEligibility: evaluateTidyTreeEligibility,
  },
  {
    id: TREE_OF_LIFE_CHART_ID,
    name: "Tree of life",
    summary: "Radial cluster tree with leaf labels on a common rim.",
    evaluateEligibility: evaluateTidyTreeEligibility,
  },
];

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

function formatAttributeValue(attribute: AttributeDescription | null | undefined, value: unknown): string {
  const formattedValue = formatInstanceValue(value);
  if (attribute === null || attribute === undefined || typeof value !== "string") {
    return formattedValue;
  }
  if (attribute.dataType === "DATE") {
    const parsed = parseIsoDate(value);
    return parsed === null
      ? formattedValue
      : new Intl.DateTimeFormat(undefined, { year: "numeric", month: "numeric", day: "numeric" }).format(parsed);
  }
  if (attribute.dataType === "TIME") {
    const parsed = parseIsoTime(value);
    return parsed === null
      ? formattedValue
      : new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit", second: "2-digit" }).format(parsed);
  }
  if (attribute.dataType === "DATETIME") {
    const parsed = parseIsoDateTime(value);
    return parsed === null
      ? formattedValue
      : new Intl.DateTimeFormat(undefined, {
        year: "numeric",
        month: "numeric",
        day: "numeric",
        hour: "numeric",
        minute: "2-digit",
        second: value.length > 16 ? "2-digit" : undefined,
      }).format(parsed);
  }
  return formattedValue;
}

function parseIsoDate(value: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (match === null) {
    return null;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const parsed = new Date(year, month - 1, day);
  return parsed.getFullYear() === year && parsed.getMonth() === month - 1 && parsed.getDate() === day ? parsed : null;
}

function parseIsoTime(value: string): Date | null {
  const match = /^(\d{2}):(\d{2}):(\d{2})$/.exec(value);
  if (match === null) {
    return null;
  }
  const hour = Number(match[1]);
  const minute = Number(match[2]);
  const second = Number(match[3]);
  if (hour > 23 || minute > 59 || second > 59) {
    return null;
  }
  return new Date(1970, 0, 1, hour, minute, second);
}

function parseIsoDateTime(value: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(value);
  if (match === null) {
    return null;
  }
  const date = parseIsoDate(`${match[1]}-${match[2]}-${match[3]}`);
  if (date === null) {
    return null;
  }
  const hour = Number(match[4]);
  const minute = Number(match[5]);
  const second = match[6] === undefined ? 0 : Number(match[6]);
  if (hour > 23 || minute > 59 || second > 59) {
    return null;
  }
  return new Date(date.getFullYear(), date.getMonth(), date.getDate(), hour, minute, second);
}

function isOrderedDataType(dataType: string): boolean {
  return dataType === "NUMERIC" || dataType === "DATE" || dataType === "TIME" || dataType === "DATETIME";
}

function isSpatialDataType(dataType: string): boolean {
  return dataType === "LOCATION" || dataType === "LOCATION_LINE" || dataType === "LOCATION_AREA";
}

function criterionValueError(attribute: AttributeDescription, rawValue: string): string | null {
  if (attribute.dataType === "NUMERIC" && !Number.isFinite(parseCriterionValue(attribute, rawValue))) {
    return "numeric value must be valid";
  }
  if (attribute.dataType === "DATE" && parseIsoDate(rawValue.trim()) === null) {
    return "date value must use YYYY-MM-DD";
  }
  if (attribute.dataType === "TIME" && parseIsoTime(rawValue.trim()) === null) {
    return "time value must use HH:mm:ss";
  }
  if (attribute.dataType === "DATETIME" && parseIsoDateTime(rawValue.trim()) === null) {
    return "datetime value must use local ISO minute or second precision";
  }
  return null;
}

function inputTypeFor(attribute: AttributeDescription | null | undefined): string {
  if (attribute?.dataType === "NUMERIC") {
    return "number";
  }
  if (attribute?.dataType === "URL") {
    return "url";
  }
  if (attribute?.dataType === "DATE") {
    return "date";
  }
  if (attribute?.dataType === "TIME") {
    return "time";
  }
  if (attribute?.dataType === "DATETIME") {
    return "datetime-local";
  }
  return "text";
}

function inputStepFor(attribute: AttributeDescription | null | undefined): string | undefined {
  if (attribute?.dataType === "NUMERIC") {
    return "any";
  }
  if (attribute?.dataType === "TIME" || attribute?.dataType === "DATETIME") {
    return "1";
  }
  return undefined;
}

function queryOperatorsFor(attribute: AttributeDescription | null): QueryOperator[] {
  if (attribute === null) {
    return ["="];
  }
  if (isOrderedDataType(attribute.dataType)) {
    return ["=", "<", ">"];
  }
  if (isSpatialDataType(attribute.dataType)) {
    return ["="];
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

function evaluateTidyTreeEligibility(apiDescription: ApiDescriptionResponse): ChartEligibility {
  if (apiDescription.entities.length === 0) {
    return {
      selectable: false,
      reason: "The selected model has no entities.",
    };
  }
  const entityNames = new Set(apiDescription.entities.map((entity) => entity.azName.toLocaleLowerCase()));
  const hasAcyclicAssociation = (apiDescription.associations ?? []).some((association) => (
    entityNames.has(association.sourceEntityAzName.toLocaleLowerCase())
    && entityNames.has(association.targetEntityAzName.toLocaleLowerCase())
  ));
  if (!hasAcyclicAssociation) {
    return {
      selectable: false,
      reason: "Tree charts need at least one association between entities.",
    };
  }
  return { selectable: true };
}

function defaultLabelTemplate(entity: EntityDescription | null): string {
  return entity?.attributes[0]?.azName ? `{${entity.attributes[0].azName}}` : "{id}";
}

function defaultRootDirectCriterion(entity: EntityDescription | null): TidyTreeRootDirectCriterion {
  const attribute = entity?.attributes[0] ?? null;
  return {
    attributeAzName: attribute?.azName ?? "",
    operator: "=",
    value: "",
  };
}

function defaultRootRelationshipCriterion(
  entity: EntityDescription | null,
  apiDescription: ApiDescriptionResponse | null,
): TidyTreeRootRelationshipCriterion {
  const traversal = traversalOptionsFor(entity, apiDescription)[0] ?? null;
  const relatedAttribute = traversal?.relatedEntity.attributes[0] ?? null;
  return {
    traversalValue: traversal === null ? "" : traversalOptionValue(traversal),
    relatedAttributeAzName: relatedAttribute?.azName ?? "",
    operator: "=",
    value: "",
  };
}

function defaultRootSelection(entity: EntityDescription | null, apiDescription: ApiDescriptionResponse | null, mode: TidyTreeRootMode = "manual"): TidyTreeRootSelection {
  return {
    mode,
    labelTemplate: defaultLabelTemplate(entity),
    directCriteria: [defaultRootDirectCriterion(entity)],
    relationshipCriteria: [],
  };
}

function defaultLevelFilter(entity: EntityDescription | null): TidyTreeLevelFilter {
  return {
    enabled: false,
    directCriteria: [defaultRootDirectCriterion(entity)],
    relationshipCriteria: [],
  };
}

function templatePlaceholders(template: string): string[] {
  return [...template.matchAll(/\{([^{}]+)\}/g)]
    .map((match) => match[1].trim())
    .filter((placeholder) => placeholder.length > 0);
}

function validateLabelTemplate(entity: EntityDescription, template: string): string | null {
  if (!template.trim()) {
    return "Label template is required.";
  }
  const attributeNames = new Set(entity.attributes.map((attribute) => attribute.azName.toLocaleLowerCase()));
  for (const placeholder of templatePlaceholders(template)) {
    if (placeholder === "id") {
      continue;
    }
    if (!attributeNames.has(placeholder.toLocaleLowerCase())) {
      return `${placeholder} is not an attribute of ${entity.visName}.`;
    }
  }
  return null;
}

function renderLabelTemplate(entity: EntityDescription, instance: EntityInstanceResponse, template: string): string {
  const rendered = template.replace(/\{([^{}]+)\}/g, (_match, rawPlaceholder: string) => {
    const placeholder = rawPlaceholder.trim();
    if (placeholder === "id") {
      return instance.id;
    }
    const attribute = entity.attributes.find((candidate) => sameAzName(candidate.azName, placeholder));
    if (attribute === undefined) {
      return "";
    }
    return formatAttributeValue(attribute, instance.values[attribute.azName]);
  }).trim();
  return rendered || instance.id;
}

function selectedEntityNames(binding: TidyTreeBinding): Set<string> {
  return new Set(binding.levels.map((level) => level.entityAzName.toLocaleLowerCase()).filter(Boolean));
}

function isSelfAssociationTraversal(option: TraversalOption): boolean {
  return sameAzName(option.association.sourceEntityAzName, option.association.targetEntityAzName);
}

function traversalOptionsForBindingLevel(
  apiDescription: ApiDescriptionResponse | null,
  binding: TidyTreeBinding,
  levelIndex: number,
): TraversalOption[] {
  if (apiDescription === null || levelIndex <= 0) {
    return [];
  }
  const previousLevel = binding.levels[levelIndex - 1];
  const previousEntity = findEntity(apiDescription.entities, previousLevel.entityAzName);
  const usedEntityNames = selectedEntityNames({
    ...binding,
    levels: binding.levels.slice(0, levelIndex),
  });
  return traversalOptionsFor(previousEntity, apiDescription)
    .filter((option) => isSelfAssociationTraversal(option) || !usedEntityNames.has(option.relatedEntity.azName.toLocaleLowerCase()));
}

function selectedRootEntity(apiDescription: ApiDescriptionResponse | null, binding: TidyTreeBinding): EntityDescription | null {
  return findEntity(apiDescription?.entities ?? [], binding.levels[0]?.entityAzName ?? "");
}

function selectedRelationshipTraversal(
  apiDescription: ApiDescriptionResponse | null,
  entity: EntityDescription | null,
  criterion: TidyTreeRootRelationshipCriterion,
): TraversalOption | null {
  return traversalOptionsFor(entity, apiDescription)
    .find((option) => traversalOptionValue(option) === criterion.traversalValue) ?? null;
}

function validateDirectCriterion(
  entity: EntityDescription,
  criterion: TidyTreeRootDirectCriterion,
  index: number,
  contextLabel: string,
): string | null {
  const attribute = entity.attributes.find((candidate) => candidate.azName === criterion.attributeAzName) ?? null;
  if (attribute === null) {
    return `${contextLabel} comparison ${index + 1}: select an attribute.`;
  }
  if (!queryOperatorsFor(attribute).includes(criterion.operator)) {
    return `${contextLabel} comparison ${index + 1}: ${criterion.operator} is not valid for ${attribute.visName}.`;
  }
  if (!criterion.value.trim()) {
    return `${contextLabel} comparison ${index + 1}: value is required.`;
  }
  const valueError = criterionValueError(attribute, criterion.value);
  if (valueError !== null) {
    return `${contextLabel} comparison ${index + 1}: ${valueError}.`;
  }
  return null;
}

function validateRelationshipCriterion(
  apiDescription: ApiDescriptionResponse,
  entity: EntityDescription,
  criterion: TidyTreeRootRelationshipCriterion,
  index: number,
  contextLabel: string,
): string | null {
  const traversal = selectedRelationshipTraversal(apiDescription, entity, criterion);
  if (traversal === null) {
    return `${contextLabel} relationship ${index + 1}: select an association.`;
  }
  const attribute = traversal.relatedEntity.attributes.find((candidate) => candidate.azName === criterion.relatedAttributeAzName) ?? null;
  if (attribute === null) {
    return `${contextLabel} relationship ${index + 1}: select a related attribute.`;
  }
  if (!queryOperatorsFor(attribute).includes(criterion.operator)) {
    return `${contextLabel} relationship ${index + 1}: ${criterion.operator} is not valid for ${attribute.visName}.`;
  }
  if (!criterion.value.trim()) {
    return `${contextLabel} relationship ${index + 1}: value is required.`;
  }
  const valueError = criterionValueError(attribute, criterion.value);
  if (valueError !== null) {
    return `${contextLabel} relationship ${index + 1}: ${valueError}.`;
  }
  return null;
}

function criteriaQueryRequest(
  apiDescription: ApiDescriptionResponse,
  entity: EntityDescription,
  directCriteria: TidyTreeRootDirectCriterion[],
  relationshipCriteria: TidyTreeRootRelationshipCriterion[],
): QueryRequest {
  const comparisons = directCriteria.map((criterion) => {
    const attribute = entity.attributes.find((candidate) => candidate.azName === criterion.attributeAzName);
    if (attribute === undefined) {
      throw new Error(`Attribute ${criterion.attributeAzName} is unavailable.`);
    }
    return {
      attributeAzName: attribute.azName,
      operator: criterion.operator,
      value: parseCriterionValue(attribute, criterion.value),
    };
  });
  const relationships = relationshipCriteria.map((criterion) => {
    const traversal = selectedRelationshipTraversal(apiDescription, entity, criterion);
    if (traversal === null) {
      throw new Error("Relationship association is unavailable.");
    }
    const attribute = traversal.relatedEntity.attributes.find((candidate) => candidate.azName === criterion.relatedAttributeAzName);
    if (attribute === undefined) {
      throw new Error(`Related attribute ${criterion.relatedAttributeAzName} is unavailable.`);
    }
    return {
      associationAzName: traversal.association.azName,
      direction: traversal.direction,
      entityAzName: traversal.relatedEntity.azName,
      where: {
        comparisons: [{
          attributeAzName: attribute.azName,
          operator: criterion.operator,
          value: parseCriterionValue(attribute, criterion.value),
        }],
      },
    };
  });
  return {
    where: comparisons.length === 0 ? undefined : { comparisons },
    relationships: relationships.length === 0 ? undefined : relationships,
  };
}

function rootSelectionValidationMessage(apiDescription: ApiDescriptionResponse | null, binding: TidyTreeBinding): string | null {
  if (apiDescription === null || binding.rootSelection.mode !== "entity") {
    return null;
  }
  const rootEntity = selectedRootEntity(apiDescription, binding);
  if (rootEntity === null) {
    return "Select a root entity type.";
  }
  const labelTemplateError = validateLabelTemplate(rootEntity, binding.rootSelection.labelTemplate);
  if (labelTemplateError !== null) {
    return `Root label: ${labelTemplateError}`;
  }
  if (binding.rootSelection.directCriteria.length === 0) {
    return "Add at least one root comparison.";
  }
  for (const [index, criterion] of binding.rootSelection.directCriteria.entries()) {
    const criterionError = validateDirectCriterion(rootEntity, criterion, index, "Root");
    if (criterionError !== null) {
      return criterionError;
    }
  }
  for (const [index, criterion] of binding.rootSelection.relationshipCriteria.entries()) {
    const criterionError = validateRelationshipCriterion(apiDescription, rootEntity, criterion, index, "Root");
    if (criterionError !== null) {
      return criterionError;
    }
  }
  return null;
}

function levelOneFilterValidationMessage(apiDescription: ApiDescriptionResponse | null, binding: TidyTreeBinding): string | null {
  if (apiDescription === null || binding.rootSelection.mode !== "manual") {
    return null;
  }
  const level = binding.levels[0] ?? null;
  const filter = level?.filter ?? null;
  if (level === null || filter === null || !filter.enabled) {
    return null;
  }
  const entity = findEntity(apiDescription.entities, level.entityAzName);
  if (entity === null) {
    return "Level 1 filter needs a valid entity.";
  }
  if (filter.directCriteria.length === 0 && filter.relationshipCriteria.length === 0) {
    return "Level 1 filter needs at least one comparison or relationship criterion.";
  }
  for (const [index, criterion] of filter.directCriteria.entries()) {
    const criterionError = validateDirectCriterion(entity, criterion, index, "Level 1");
    if (criterionError !== null) {
      return criterionError;
    }
  }
  for (const [index, criterion] of filter.relationshipCriteria.entries()) {
    const criterionError = validateRelationshipCriterion(apiDescription, entity, criterion, index, "Level 1");
    if (criterionError !== null) {
      return criterionError;
    }
  }
  return null;
}

function rootSelectionQueryRequest(apiDescription: ApiDescriptionResponse, binding: TidyTreeBinding): QueryRequest {
  const rootEntity = selectedRootEntity(apiDescription, binding);
  if (rootEntity === null) {
    throw new Error("Select a root entity type.");
  }
  return criteriaQueryRequest(apiDescription, rootEntity, binding.rootSelection.directCriteria, binding.rootSelection.relationshipCriteria);
}

async function resolveTidyTreeRootInstances(
  apiBaseUrl: string,
  modelAzName: string,
  instanceRootId: string,
  apiDescription: ApiDescriptionResponse,
  binding: TidyTreeBinding,
): Promise<EntityInstanceResponse[]> {
  const rootEntity = selectedRootEntity(apiDescription, binding);
  if (rootEntity === null) {
    throw new Error("Select a root entity type.");
  }
  return queryEntityInstances(
    apiBaseUrl,
    modelAzName,
    instanceRootId,
    rootEntity.azName,
    rootSelectionQueryRequest(apiDescription, binding),
  );
}

async function resolveTidyTreeLevelOneFilterInstances(
  apiBaseUrl: string,
  modelAzName: string,
  instanceRootId: string,
  apiDescription: ApiDescriptionResponse,
  binding: TidyTreeBinding,
): Promise<EntityInstanceResponse[]> {
  const level = binding.levels[0] ?? null;
  const filter = level?.filter ?? null;
  const entity = level === null ? null : findEntity(apiDescription.entities, level.entityAzName);
  if (level === null || filter === null || !filter.enabled || entity === null) {
    throw new Error("Level 1 filter is unavailable.");
  }
  return queryEntityInstances(
    apiBaseUrl,
    modelAzName,
    instanceRootId,
    entity.azName,
    criteriaQueryRequest(apiDescription, entity, filter.directCriteria, filter.relationshipCriteria),
  );
}

function bindingValidationMessage(
  apiDescription: ApiDescriptionResponse | null,
  binding: TidyTreeBinding,
  rootMatchState?: RootMatchState,
  levelOneFilterMatchState?: RootMatchState,
): string | null {
  if (apiDescription === null) {
    return "Model metadata is not loaded.";
  }
  if (binding.rootSelection.mode === "manual" && !binding.rootLabel.trim()) {
    return "Chart root label is required.";
  }
  if (binding.levels.length === 0 || !binding.levels[0].entityAzName) {
    return "Select at least one entity level.";
  }
  const rootSelectionError = rootSelectionValidationMessage(apiDescription, binding);
  if (rootSelectionError !== null) {
    return rootSelectionError;
  }
  const levelOneFilterError = levelOneFilterValidationMessage(apiDescription, binding);
  if (levelOneFilterError !== null) {
    return levelOneFilterError;
  }
  const levelOneFilter = binding.levels[0]?.filter ?? null;
  if (binding.rootSelection.mode === "manual" && levelOneFilter?.enabled && levelOneFilterMatchState !== undefined) {
    if (levelOneFilterMatchState.status === "loading") {
      return "Resolving Level 1 filter.";
    }
    if (levelOneFilterMatchState.status === "error") {
      return levelOneFilterMatchState.message;
    }
    if (levelOneFilterMatchState.count === 0) {
      return "Level 1 start condition did not match any results.";
    }
    if (levelOneFilterMatchState.count === undefined) {
      return "Resolve Level 1 filter before visualizing.";
    }
  }
  if (binding.rootSelection.mode === "entity") {
    if (rootMatchState === undefined) {
      return null;
    }
    if (rootMatchState.status === "loading") {
      return "Resolving root instance.";
    }
    if (rootMatchState.status === "error") {
      return rootMatchState.message;
    }
    if (rootMatchState.count !== 1) {
      return rootMatchState.count === undefined
        ? "Resolve root instance before visualizing."
        : `Root selection must match exactly one instance; currently matched ${rootMatchState.count}.`;
    }
  }
  const seenEntities = new Set<string>();
  for (const [index, level] of binding.levels.entries()) {
    const entity = findEntity(apiDescription.entities, level.entityAzName);
    if (entity === null) {
      return `Level ${index + 1} entity is missing.`;
    }
    const entityKey = entity.azName.toLocaleLowerCase();
    if (seenEntities.has(entityKey)) {
      const association = (apiDescription.associations ?? []).find((candidate) => (
        level.traversal !== undefined
        && candidate.azName === level.traversal.associationAzName
      ));
      const isAllowedRecursiveStep = index > 0
        && association !== undefined
        && sameAzName(association.sourceEntityAzName, association.targetEntityAzName)
        && sameAzName(binding.levels[index - 1]?.entityAzName ?? "", entity.azName);
      if (!isAllowedRecursiveStep) {
        return `${entity.visName} is already used in this tree path.`;
      }
    }
    seenEntities.add(entityKey);
    const templateError = validateLabelTemplate(entity, level.labelTemplate);
    if (templateError !== null) {
      return `Level ${index + 1}: ${templateError}`;
    }
    if (index > 0 && level.traversal === undefined) {
      return `Level ${index + 1} needs an association from the previous level.`;
    }
  }
  return null;
}

function linkChildIdForParent(parentId: string, link: AssociationLinkResponse, direction: RelationshipDirection): string | null {
  if (direction === "outgoing") {
    return link.sourceInstanceId === parentId ? link.targetInstanceId : null;
  }
  return link.targetInstanceId === parentId ? link.sourceInstanceId : null;
}

function sortInstancesByLabel(entity: EntityDescription, instances: EntityInstanceResponse[], template: string): EntityInstanceResponse[] {
  return [...instances].sort((left, right) => (
    renderLabelTemplate(entity, left, template).localeCompare(renderLabelTemplate(entity, right, template))
  ));
}

async function buildTidyTreeData(
  apiBaseUrl: string,
  modelAzName: string,
  instanceRootId: string,
  apiDescription: ApiDescriptionResponse,
  binding: TidyTreeBinding,
): Promise<TidyTreeNode> {
  const validationError = bindingValidationMessage(apiDescription, binding);
  if (validationError !== null) {
    throw new Error(validationError);
  }

  const entityByAzName = new Map(apiDescription.entities.map((entity) => [entity.azName.toLocaleLowerCase(), entity]));
  const selectedRootInstances = binding.rootSelection.mode === "entity"
    ? await resolveTidyTreeRootInstances(apiBaseUrl, modelAzName, instanceRootId, apiDescription, binding)
    : [];
  if (binding.rootSelection.mode === "entity" && selectedRootInstances.length !== 1) {
    throw new Error(`Root selection must match exactly one instance; currently matched ${selectedRootInstances.length}.`);
  }
  const instancesByLevel = new Map<number, EntityInstanceResponse[]>();
  await Promise.all(binding.levels.map(async (level, levelIndex) => {
    if (binding.rootSelection.mode === "entity" && levelIndex === 0) {
      instancesByLevel.set(levelIndex, selectedRootInstances);
      return;
    }
    if (binding.rootSelection.mode === "manual" && levelIndex === 0 && level.filter?.enabled) {
      const entity = findEntity(apiDescription.entities, level.entityAzName);
      if (entity === null) {
        throw new Error("Level 1 filter entity is unavailable.");
      }
      const instances = await queryEntityInstances(
        apiBaseUrl,
        modelAzName,
        instanceRootId,
        level.entityAzName,
        criteriaQueryRequest(apiDescription, entity, level.filter.directCriteria, level.filter.relationshipCriteria),
      );
      instancesByLevel.set(levelIndex, instances);
      return;
    }
    const instances = await queryEntityInstances(apiBaseUrl, modelAzName, instanceRootId, level.entityAzName, {});
    instancesByLevel.set(levelIndex, instances);
  }));

  const linksByLevel = new Map<number, AssociationLinkResponse[]>();
  await Promise.all(binding.levels.slice(1).map(async (level, index) => {
    if (level.traversal === undefined) {
      return;
    }
    const links = await fetchAssociationLinks(apiBaseUrl, modelAzName, instanceRootId, level.traversal.associationAzName);
    linksByLevel.set(index + 1, links);
  }));

  function buildLevelNode(levelIndex: number, instance: EntityInstanceResponse, visitedPath = new Set<string>(), labelTemplateOverride?: string): TidyTreeNode {
    const level = binding.levels[levelIndex];
    const entity = entityByAzName.get(level.entityAzName.toLocaleLowerCase());
    if (entity === undefined) {
      throw new Error(`Entity ${level.entityAzName} is unavailable.`);
    }
    const nextVisitedPath = new Set(visitedPath);
    nextVisitedPath.add(instance.id);

    const nextLevel = binding.levels[levelIndex + 1] ?? null;
    let children: TidyTreeNode[] = [];
    if (nextLevel !== null && nextLevel.traversal !== undefined) {
      const nextEntity = entityByAzName.get(nextLevel.entityAzName.toLocaleLowerCase());
      const nextInstances = instancesByLevel.get(levelIndex + 1) ?? [];
      const nextInstanceById = new Map(nextInstances.map((candidate) => [candidate.id, candidate]));
      const links = linksByLevel.get(levelIndex + 1) ?? [];
      const childIds = new Set<string>();
      for (const link of links) {
        const childId = linkChildIdForParent(instance.id, link, nextLevel.traversal.direction);
        if (childId !== null && !nextVisitedPath.has(childId)) {
          childIds.add(childId);
        }
      }
      const childInstances = [...childIds]
        .map((childId) => nextInstanceById.get(childId) ?? null)
        .filter((candidate): candidate is EntityInstanceResponse => candidate !== null);
      children = nextEntity === undefined
        ? []
        : sortInstancesByLabel(nextEntity, childInstances, nextLevel.labelTemplate)
            .map((childInstance) => buildLevelNode(levelIndex + 1, childInstance, nextVisitedPath));
    }

    return {
      id: instance.id,
      label: renderLabelTemplate(entity, instance, labelTemplateOverride ?? level.labelTemplate),
      detail: entity.visName,
      children,
    };
  }

  const firstLevel = binding.levels[0];
  const firstEntity = entityByAzName.get(firstLevel.entityAzName.toLocaleLowerCase());
  const firstInstances = instancesByLevel.get(0) ?? [];
  if (binding.rootSelection.mode === "entity") {
    if (firstEntity === undefined || firstInstances[0] === undefined) {
      throw new Error("Resolved root instance is unavailable.");
    }
    return buildLevelNode(0, firstInstances[0], new Set<string>(), binding.rootSelection.labelTemplate);
  }
  return {
    id: "root",
    label: binding.rootLabel.trim(),
    detail: apiDescription.modelVisName,
    children: firstEntity === undefined
      ? []
      : sortInstancesByLabel(firstEntity, firstInstances, firstLevel.labelTemplate)
          .map((instance) => buildLevelNode(0, instance)),
  };
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

function modelInstanceApiUrl(modelAzName: string, instanceRootId: string): string {
  const params = new URLSearchParams({
    modelAzName,
    instanceRootId,
  });
  return `/modelInstanceApi?${params.toString()}`;
}

function visualizeWizardUrl(modelAzName: string, instanceRootId: string): string {
  const params = new URLSearchParams({
    modelAzName,
    instanceRootId,
  });
  return `/visualizeWizard?${params.toString()}`;
}

function resolvedApiPath(pathTemplate: string, modelAzName: string, instanceRootId: string): string {
  return pathTemplate
    .split("{modelAzName}").join(encodeURIComponent(modelAzName))
    .split("{instanceRootId}").join(encodeURIComponent(instanceRootId))
    .split("{instanceId}").join("{instanceId}");
}

function methodForEntityOperation(operationName: string): string {
  switch (operationName) {
    case "create":
    case "query":
      return "POST";
    case "update":
      return "PUT";
    default:
      return "GET";
  }
}

function methodForAssociationOperation(operationName: string): string {
  return operationName === "create" ? "POST" : "GET";
}

function entityOperationPurpose(operationName: string, entity: EntityDescription): string {
  switch (operationName) {
    case "create":
      return `Create one ${entity.visName} instance.`;
    case "list":
      return `List ${entity.visName} instances for the selected model instance.`;
    case "read":
      return `Read one ${entity.visName} instance by backend-assigned instance id.`;
    case "update":
      return `Overwrite one ${entity.visName} instance after value validation.`;
    case "query":
      return `Query ${entity.visName} instances with scalar and relationship criteria.`;
    case "count":
      return `Count ${entity.visName} instances in the selected model instance.`;
    default:
      return `Use the ${operationName} operation for ${entity.visName}.`;
  }
}

function associationOperationPurpose(operationName: string, association: AssociationDescription): string {
  switch (operationName) {
    case "create":
      return `Create one ${association.visName} source-to-target instance link.`;
    case "list":
      return `List ${association.visName} links in the selected model instance.`;
    default:
      return `Use the ${operationName} operation for ${association.visName}.`;
  }
}

function exampleValueFor(attribute: AttributeDescription): unknown {
  switch (attribute.dataType) {
    case "NUMERIC":
      return 123.45;
    case "URL":
      return "https://example.com";
    case "DATA":
      return "data";
    case "DATE":
      return "2026-08-12";
    case "TIME":
      return "18:30:00";
    case "DATETIME":
      return "2026-08-12T18:30";
    case "LOCATION":
      return { latitude: 62.1234567, longitude: 30.1234567 };
    case "LOCATION_LINE":
      return {
        locations: [
          { latitude: 62.1234567, longitude: 30.1234567 },
          { latitude: 62.2234567, longitude: 30.2234567 },
        ],
      };
    case "LOCATION_AREA":
      return {
        boundary: [
          { latitude: 62.1234567, longitude: 30.1234567 },
          { latitude: 62.2234567, longitude: 30.2234567 },
          { latitude: 62.1234567, longitude: 30.3234567 },
        ],
      };
    default:
      return "text";
  }
}

function entityBodyExample(entity: EntityDescription): Record<string, unknown> {
  if (Object.keys(entity.createBodyExample).length > 0) {
    return Object.fromEntries(entity.attributes.map((attribute) => [
      attribute.azName,
      attribute.dataType === "NUMERIC"
        ? Number(entity.createBodyExample[attribute.azName] ?? exampleValueFor(attribute))
        : entity.createBodyExample[attribute.azName] ?? exampleValueFor(attribute),
    ]));
  }
  return Object.fromEntries(entity.attributes.map((attribute) => [attribute.azName, exampleValueFor(attribute)]));
}

function entityQueryExample(entity: EntityDescription): Record<string, unknown> {
  const attribute = entity.attributes[0] ?? null;
  if (attribute === null) {
    return {};
  }
  return {
    where: {
      comparisons: [
        {
          attributeAzName: attribute.azName,
          operator: "=",
          value: exampleValueFor(attribute),
        },
      ],
    },
  };
}

function entityResponseExample(operationName: string, entity: EntityDescription, apiDescription: ApiDescriptionResponse, instanceRootId: string): unknown {
  const instance = {
    id: "00000000-0000-0000-0000-000000000000",
    modelAzName: apiDescription.modelAzName,
    modelVersion: apiDescription.modelVersion ?? "0.0.0",
    entityAzName: entity.azName,
    values: entityBodyExample(entity),
  };
  switch (operationName) {
    case "list":
    case "query":
      return [instance];
    case "count":
      return { count: 1 };
    default:
      return {
        ...instance,
        instanceRootId,
      };
  }
}

function entityRequestExample(operationName: string, entity: EntityDescription): unknown {
  switch (operationName) {
    case "create":
    case "update":
      return entityBodyExample(entity);
    case "query":
      return entityQueryExample(entity);
    default:
      return null;
  }
}

function associationBodyExample(association: AssociationDescription): Record<string, unknown> {
  return {
    sourceInstanceId: association.createBodyExample?.sourceInstanceId ?? "00000000-0000-0000-0000-000000000000",
    targetInstanceId: association.createBodyExample?.targetInstanceId ?? "11111111-1111-1111-1111-111111111111",
  };
}

function associationResponseExample(operationName: string, association: AssociationDescription, apiDescription: ApiDescriptionResponse): unknown {
  const link = {
    id: "00000000-0000-0000-0000-000000000000",
    modelAzName: apiDescription.modelAzName,
    associationAzName: association.azName,
    ...associationBodyExample(association),
  };
  return operationName === "list" ? [link] : link;
}

function formatJsonExample(value: unknown): string {
  if (value === null) {
    return "No request body";
  }
  return JSON.stringify(value, null, 2);
}

function formatEditableJson(value: unknown): string {
  if (value === null) {
    return "";
  }
  return JSON.stringify(value, null, 2);
}

function absoluteApiUrl(apiBaseUrl: string, path: string): string {
  if (!apiBaseUrl) {
    return path;
  }
  return `${apiBaseUrl}${path.startsWith("/") ? path : `/${path}`}`;
}

function formatResponseBody(responseText: string): string {
  if (!responseText) {
    return "";
  }
  try {
    return JSON.stringify(JSON.parse(responseText), null, 2);
  } catch {
    return responseText;
  }
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
    const trimmedValue = rawValue.trim();
    if (!trimmedValue) {
      continue;
    }
    if (attribute.dataType === "NUMERIC") {
      const numericValue = Number(trimmedValue);
      if (!Number.isFinite(numericValue)) {
        throw new Error(`${attribute.visName} must be a valid number`);
      }
      values[attribute.azName] = numericValue;
    } else {
      const valueError = criterionValueError(attribute, trimmedValue);
      if (valueError !== null) {
        throw new Error(`${attribute.visName} ${valueError}`);
      }
      values[attribute.azName] = trimmedValue;
    }
  }
  if (Object.keys(values).length === 0) {
    throw new Error("Fill at least one attribute");
  }
  return values;
}

function associationEndpointLabel(entity: EntityDescription | null, roleName?: string | null): string {
  const roleLabel = roleName?.trim();
  if (entity === null) {
    return roleLabel || "Entity";
  }
  return roleLabel ? `${entity.visName} (${roleLabel})` : entity.visName;
}

function relatedInstanceIdForLink(resultId: string, link: AssociationLinkResponse, direction: RelationshipDirection): string | null {
  if (direction === "outgoing") {
    return link.sourceInstanceId === resultId ? link.targetInstanceId : null;
  }
  return link.targetInstanceId === resultId ? link.sourceInstanceId : null;
}

function matchesQueryComparison(
  value: unknown,
  comparison: QueryComparisonRequest,
  attribute: AttributeDescription | null | undefined,
): boolean {
  if (value === null || value === undefined) {
    return false;
  }
  if (comparison.operator === "contains") {
    return typeof value === "string"
      && typeof comparison.value === "string"
      && value.toLowerCase().includes(comparison.value.toLowerCase());
  }
  if (comparison.operator === "<") {
    if (attribute?.dataType === "NUMERIC") {
      return typeof value === "number" && typeof comparison.value === "number" && value < comparison.value;
    }
    if (isOrderedDataType(attribute?.dataType ?? "")) {
      const compared = compareTemporalValues(attribute, value, comparison.value);
      return compared !== null && compared < 0;
    }
    return typeof value === "string" && typeof comparison.value === "string" && value < comparison.value;
  }
  if (comparison.operator === ">") {
    if (attribute?.dataType === "NUMERIC") {
      return typeof value === "number" && typeof comparison.value === "number" && value > comparison.value;
    }
    if (isOrderedDataType(attribute?.dataType ?? "")) {
      const compared = compareTemporalValues(attribute, value, comparison.value);
      return compared !== null && compared > 0;
    }
    return typeof value === "string" && typeof comparison.value === "string" && value > comparison.value;
  }
  if (attribute?.dataType === "DATETIME" && typeof value === "string" && typeof comparison.value === "string") {
    const actual = parseIsoDateTime(value);
    const expected = parseIsoDateTime(comparison.value);
    return actual !== null && expected !== null && actual.getTime() === expected.getTime();
  }
  return formatInstanceValue(value) === formatInstanceValue(comparison.value);
}

function compareTemporalValues(
  attribute: AttributeDescription | null | undefined,
  left: unknown,
  right: unknown,
): number | null {
  if (typeof left !== "string" || typeof right !== "string") {
    return null;
  }
  if (attribute?.dataType === "DATE") {
    const parsedLeft = parseIsoDate(left);
    const parsedRight = parseIsoDate(right);
    return parsedLeft === null || parsedRight === null ? null : parsedLeft.getTime() - parsedRight.getTime();
  }
  if (attribute?.dataType === "TIME") {
    const parsedLeft = parseIsoTime(left);
    const parsedRight = parseIsoTime(right);
    return parsedLeft === null || parsedRight === null ? null : parsedLeft.getTime() - parsedRight.getTime();
  }
  if (attribute?.dataType === "DATETIME") {
    const parsedLeft = parseIsoDateTime(left);
    const parsedRight = parseIsoDateTime(right);
    return parsedLeft === null || parsedRight === null ? null : parsedLeft.getTime() - parsedRight.getTime();
  }
  return null;
}

function defaultEntityDisplayAttribute(entity: EntityDescription): AttributeDescription | null {
  return entity.attributes[0] ?? null;
}

function entityInstanceLabel(entity: EntityDescription, instance: EntityInstanceResponse): string {
  const displayAttribute = defaultEntityDisplayAttribute(entity);
  const displayValue = displayAttribute === null ? "" : formatAttributeValue(displayAttribute, instance.values[displayAttribute.azName]);
  return displayValue ? `${entity.visName}: ${displayValue}` : `${entity.visName}: ${instance.id}`;
}

function relationshipCriterionLabel(relationship: QueryRelationshipRequest, relatedEntity: EntityDescription): string {
  const comparison = relationship.where.comparisons[0] ?? null;
  if (comparison === null) {
    return `${relatedEntity.visName} exists`;
  }
  const attribute = relatedEntity.attributes.find((candidate) => candidate.azName === comparison.attributeAzName);
  return `${attribute?.visName ?? comparison.attributeAzName} ${comparison.operator} ${formatAttributeValue(attribute, comparison.value)}`;
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
      .filter((instance) => relationship.where.comparisons.every((comparison) => {
        const attribute = traversal.relatedEntity.attributes.find((candidate) => candidate.azName === comparison.attributeAzName);
        return matchesQueryComparison(instance.values[comparison.attributeAzName], comparison, attribute);
      }))
      .map((instance) => {
        const comparison = relationship.where.comparisons[0] ?? null;
        const attribute = comparison === null
          ? null
          : traversal.relatedEntity.attributes.find((candidate) => candidate.azName === comparison.attributeAzName);
        return {
          associationLabel: traversalLabel(traversal),
          criterionLabel: relationshipCriterionLabel(relationship, traversal.relatedEntity),
          relatedEntityLabel: entityInstanceLabel(traversal.relatedEntity, instance),
          relatedInstanceId: instance.id,
          matchedValueLabel: comparison === null ? undefined : formatAttributeValue(attribute, instance.values[comparison.attributeAzName]),
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
  const [activeEditorTab, setActiveEditorTab] = useState<EditorTab>("entity");
  const [loadedInstanceId, setLoadedInstanceId] = useState(initialInstanceId);
  const [createCopy, setCreateCopy] = useState(false);
  const [formValues, setFormValues] = useState<EditorFormValues>({});
  const [selectedAssociationAzName, setSelectedAssociationAzName] = useState("");
  const [sourceInstances, setSourceInstances] = useState<EntityInstanceResponse[]>([]);
  const [targetInstances, setTargetInstances] = useState<EntityInstanceResponse[]>([]);
  const [selectedSourceInstanceId, setSelectedSourceInstanceId] = useState("");
  const [selectedTargetInstanceId, setSelectedTargetInstanceId] = useState("");
  const [createdAssociationLink, setCreatedAssociationLink] = useState<AssociationLinkResponse | null>(null);
  const [status, setStatus] = useState<ModelInstanceLoadState>("loading");
  const [statusMessage, setStatusMessage] = useState("Loading editor...");
  const [isSaving, setIsSaving] = useState(false);
  const [isSavingAssociation, setIsSavingAssociation] = useState(false);

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
        const nextAssociationAzName = nextApiDescription.associations?.some((association) => association.azName === selectedAssociationAzName)
          ? selectedAssociationAzName
          : nextApiDescription.associations?.[0]?.azName ?? "";
        setFormValues(emptyEditorValues(nextEntity));
        setSelectedAssociationAzName(nextAssociationAzName);
        setSourceInstances([]);
        setTargetInstances([]);
        setSelectedSourceInstanceId("");
        setSelectedTargetInstanceId("");
        setCreatedAssociationLink(null);
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
  const selectedAssociation = apiDescription?.associations?.find((association) => association.azName === selectedAssociationAzName) ?? null;
  const selectedAssociationSourceEntity = selectedAssociation === null
    ? null
    : findEntity(apiDescription?.entities ?? [], selectedAssociation.sourceEntityAzName);
  const selectedAssociationTargetEntity = selectedAssociation === null
    ? null
    : findEntity(apiDescription?.entities ?? [], selectedAssociation.targetEntityAzName);
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
  }, [apiBaseUrl, selectedModelAzName, selectedRootId, selectedEntity, loadedInstanceId]);

  useEffect(() => {
    let cancelled = false;

    async function loadAssociationEntities() {
      if (
        activeEditorTab !== "associations"
        || !apiBaseUrl
        || !selectedModelAzName
        || !selectedRootId
        || selectedAssociation === null
        || selectedAssociationSourceEntity === null
        || selectedAssociationTargetEntity === null
      ) {
        return;
      }

      setStatus("loading");
      setStatusMessage("Loading association endpoints...");
      try {
        const [nextSourceInstances, nextTargetInstances] = await Promise.all([
          queryEntityInstances(apiBaseUrl, selectedModelAzName, selectedRootId, selectedAssociationSourceEntity.azName, {}),
          queryEntityInstances(apiBaseUrl, selectedModelAzName, selectedRootId, selectedAssociationTargetEntity.azName, {}),
        ]);
        if (cancelled) {
          return;
        }
        setSourceInstances(nextSourceInstances);
        setTargetInstances(nextTargetInstances);
        setSelectedSourceInstanceId((current) => nextSourceInstances.some((instance) => instance.id === current) ? current : nextSourceInstances[0]?.id ?? "");
        setSelectedTargetInstanceId((current) => nextTargetInstances.some((instance) => instance.id === current) ? current : nextTargetInstances[0]?.id ?? "");
        setStatus("ok");
        if (nextSourceInstances.length === 0 || nextTargetInstances.length === 0) {
          setStatusMessage("Association endpoint instances are missing");
        } else {
          setStatusMessage("Ready");
        }
      } catch (error) {
        if (!cancelled) {
          setStatus("error");
          setStatusMessage(error instanceof Error ? error.message : "Association endpoints load failed");
        }
      }
    }

    void loadAssociationEntities();

    return () => {
      cancelled = true;
    };
  }, [
    activeEditorTab,
    apiBaseUrl,
    selectedModelAzName,
    selectedRootId,
    selectedAssociation,
    selectedAssociationSourceEntity,
    selectedAssociationTargetEntity,
  ]);

  function selectModel(nextModelAzName: string) {
    setSelectedModelAzName(nextModelAzName);
    setSelectedRootId("");
    setSelectedEntityAzName("");
    setSelectedAssociationAzName("");
    setSourceInstances([]);
    setTargetInstances([]);
    setSelectedSourceInstanceId("");
    setSelectedTargetInstanceId("");
    setCreatedAssociationLink(null);
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

  function selectRoot(nextRootId: string) {
    setSelectedRootId(nextRootId);
    setLoadedInstanceId("");
    setCreateCopy(false);
    setFormValues(emptyEditorValues(selectedEntity));
    setSourceInstances([]);
    setTargetInstances([]);
    setSelectedSourceInstanceId("");
    setSelectedTargetInstanceId("");
    setCreatedAssociationLink(null);
  }

  function selectAssociation(nextAssociationAzName: string) {
    setSelectedAssociationAzName(nextAssociationAzName);
    setSourceInstances([]);
    setTargetInstances([]);
    setSelectedSourceInstanceId("");
    setSelectedTargetInstanceId("");
    setCreatedAssociationLink(null);
    setStatusMessage(nextAssociationAzName ? "Ready" : "Select an association type");
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

  async function submitAssociation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!apiBaseUrl || !selectedModelAzName || !selectedRootId || selectedAssociation === null) {
      setStatus("error");
      setStatusMessage("Select model, root, and association type");
      return;
    }
    if (!selectedSourceInstanceId || !selectedTargetInstanceId) {
      setStatus("error");
      setStatusMessage("Select both bound entities");
      return;
    }

    setIsSavingAssociation(true);
    setStatus("loading");
    setStatusMessage("Creating association...");
    try {
      const saved = await createAssociationLink(
        apiBaseUrl,
        selectedModelAzName,
        selectedRootId,
        selectedAssociation.azName,
        selectedSourceInstanceId,
        selectedTargetInstanceId,
      );
      setCreatedAssociationLink(saved);
      setStatus("ok");
      setStatusMessage(`Created ${selectedAssociation.visName}`);
    } catch (error) {
      setStatus("error");
      setStatusMessage(error instanceof Error ? error.message : "Association save failed");
    } finally {
      setIsSavingAssociation(false);
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
              onChange={(event) => selectRoot(event.target.value)}
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
        </div>

        <div className="editor-tabs" role="tablist" aria-label="Editor sections">
          <button
            type="button"
            className={activeEditorTab === "entity" ? "editor-tab editor-tab-active" : "editor-tab"}
            onClick={() => setActiveEditorTab("entity")}
            role="tab"
            aria-selected={activeEditorTab === "entity"}
          >
            Entity
          </button>
          <button
            type="button"
            className={activeEditorTab === "associations" ? "editor-tab editor-tab-active" : "editor-tab"}
            onClick={() => setActiveEditorTab("associations")}
            role="tab"
            aria-selected={activeEditorTab === "associations"}
          >
            Associations
          </button>
        </div>

        {activeEditorTab === "entity" ? (
          <form className="editor-form" onSubmit={(event) => void submitEditor(event)}>
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
                  <label htmlFor={`editor-${attribute.azName}`}>
                    {attribute.visName}
                    {attribute.required ? " *" : ""}
                  </label>
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
                      type={inputTypeFor(attribute)}
                      step={inputStepFor(attribute)}
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
        ) : (
          <form className="editor-form" onSubmit={(event) => void submitAssociation(event)}>
            <div className="query-field">
              <label htmlFor="editor-association">Association type</label>
              <select
                id="editor-association"
                value={selectedAssociationAzName}
                onChange={(event) => selectAssociation(event.target.value)}
                disabled={status === "loading" || apiDescription === null || (apiDescription.associations?.length ?? 0) === 0}
              >
                {apiDescription === null || (apiDescription.associations?.length ?? 0) === 0 ? (
                  <option value="">No association types</option>
                ) : apiDescription.associations?.map((association) => (
                  <option key={association.azName} value={association.azName}>
                    {association.visName} ({association.azName}, {association.kind})
                  </option>
                ))}
              </select>
            </div>

            <div className="editor-context-grid">
              <div className="query-field">
                <label htmlFor="editor-source-instance">
                  {associationEndpointLabel(selectedAssociationSourceEntity, selectedAssociation?.sourceRoleName)}
                </label>
                <select
                  id="editor-source-instance"
                  value={selectedSourceInstanceId}
                  onChange={(event) => setSelectedSourceInstanceId(event.target.value)}
                  disabled={status === "loading" || isSavingAssociation || sourceInstances.length === 0}
                >
                  {sourceInstances.length === 0 ? (
                    <option value="">No source instances</option>
                  ) : sourceInstances.map((instance) => (
                    <option key={instance.id} value={instance.id}>
                      {selectedAssociationSourceEntity === null ? instance.id : entityInstanceLabel(selectedAssociationSourceEntity, instance)}
                    </option>
                  ))}
                </select>
              </div>
              <div className="query-field">
                <label htmlFor="editor-target-instance">
                  {associationEndpointLabel(selectedAssociationTargetEntity, selectedAssociation?.targetRoleName)}
                </label>
                <select
                  id="editor-target-instance"
                  value={selectedTargetInstanceId}
                  onChange={(event) => setSelectedTargetInstanceId(event.target.value)}
                  disabled={status === "loading" || isSavingAssociation || targetInstances.length === 0}
                >
                  {targetInstances.length === 0 ? (
                    <option value="">No target instances</option>
                  ) : targetInstances.map((instance) => (
                    <option key={instance.id} value={instance.id}>
                      {selectedAssociationTargetEntity === null ? instance.id : entityInstanceLabel(selectedAssociationTargetEntity, instance)}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {createdAssociationLink !== null && (
              <div className="editor-link-summary">
                <span>Created link</span>
                <strong>{createdAssociationLink.id}</strong>
              </div>
            )}

            <div className="editor-actions">
              <span className={`model-status model-status-${status}`}>{statusMessage}</span>
              <button
                type="submit"
                disabled={
                  isSavingAssociation
                  || status === "loading"
                  || selectedAssociation === null
                  || !selectedRootId
                  || !selectedSourceInstanceId
                  || !selectedTargetInstanceId
                }
              >
                Create
              </button>
            </div>
          </form>
        )}
      </section>
    </main>
  );
}

function ModelInstanceApiPage() {
  const modelAzName = readQueryParam("modelAzName");
  const instanceRootId = readQueryParam("instanceRootId");
  const [apiBaseUrl, setApiBaseUrl] = useState("");
  const [apiDescription, setApiDescription] = useState<ApiDescriptionResponse | null>(null);
  const [root, setRoot] = useState<ModelInstanceRootResponse | null>(null);
  const [status, setStatus] = useState<ModelInstanceLoadState>("loading");
  const [statusMessage, setStatusMessage] = useState("Loading API docs...");

  useEffect(() => {
    let cancelled = false;

    async function loadApiDocs() {
      if (!modelAzName || !instanceRootId) {
        setStatus("error");
        setStatusMessage("API docs URL is missing modelAzName or instanceRootId");
        return;
      }

      try {
        const config = await loadRuntimeConfig();
        const baseUrl = normalizeBaseUrl(config.apiBaseUrl ?? "");
        if (!baseUrl) {
          throw new Error("Backend URL is not configured");
        }
        const [nextApiDescription, nextRoot] = await Promise.all([
          fetchRootModelInstanceApi(baseUrl, modelAzName, instanceRootId),
          fetchModelInstanceRoot(baseUrl, modelAzName, instanceRootId),
        ]);
        if (cancelled) {
          return;
        }
        setApiBaseUrl(baseUrl);
        setApiDescription(nextApiDescription);
        setRoot(nextRoot);
        setStatus("ok");
        setStatusMessage("API documentation ready");
      } catch (error) {
        if (!cancelled) {
          setStatus("error");
          setStatusMessage(error instanceof Error ? error.message : "API docs load failed");
        }
      }
    }

    void loadApiDocs();

    return () => {
      cancelled = true;
    };
  }, [modelAzName, instanceRootId]);

  const rootName = root === null ? instanceRootId : rootResponseDisplayName(root);
  const entityCount = apiDescription?.entities.length ?? 0;
  const associationCount = apiDescription?.associations?.length ?? 0;

  return (
    <main className="api-docs-shell">
      <header className="api-docs-header">
        <div>
          <h1>Model Instance API</h1>
          <div className="query-console-targets">
            <span>{apiDescription?.modelVisName ?? modelAzName}</span>
            <span>{rootName}</span>
          </div>
        </div>
        <a className="secondary-link" href="/?tab=modelInstances">
          Model instances
        </a>
      </header>

      <section className="api-docs-surface">
        <div className="api-docs-summary">
          <span className={`model-status model-status-${status}`}>{statusMessage}</span>
          <dl>
            <div>
              <dt>Model azName</dt>
              <dd>{apiDescription?.modelAzName ?? modelAzName}</dd>
            </div>
            <div>
              <dt>Model version</dt>
              <dd>{apiDescription?.modelVersion ?? root?.modelVersion ?? "Unknown"}</dd>
            </div>
            <div>
              <dt>Instance root</dt>
              <dd>{rootName || "Unknown"}</dd>
            </div>
            <div>
              <dt>Documented shapes</dt>
              <dd>{entityCount} entities, {associationCount} associations</dd>
            </div>
          </dl>
        </div>

        {apiDescription === null ? (
          <div className="tree-empty">API metadata unavailable</div>
        ) : (
          <>
            <section className="api-docs-section" aria-labelledby="api-docs-entities">
              <h2 id="api-docs-entities">Entities</h2>
              {apiDescription.entities.length === 0 ? (
                <div className="tree-empty">No entity types</div>
              ) : apiDescription.entities.map((entity) => (
                <article key={entity.azName} className="api-docs-type">
                  <header>
                    <h3>{entity.visName}</h3>
                    <span>{entity.azName}</span>
                  </header>
                  <div className="api-docs-fields">
                    {entity.attributes.length === 0 ? (
                      <span>No attributes</span>
                    ) : entity.attributes.map((attribute) => (
                      <span key={attribute.azName}>
                        <strong>{attribute.visName}</strong>
                        {attribute.azName} · {attribute.dataType} · {attribute.required ? "required" : "optional"}
                      </span>
                    ))}
                  </div>
                  <div className="api-docs-operations">
                    {Object.entries(entity.operations).map(([operationName, pathTemplate]) => (
                      <ApiOperation
                        key={`${entity.azName}-${operationName}`}
                        method={methodForEntityOperation(operationName)}
                        name={operationName}
                        path={resolvedApiPath(pathTemplate, apiDescription.modelAzName, instanceRootId)}
                        purpose={entityOperationPurpose(operationName, entity)}
                        requestExample={entityRequestExample(operationName, entity)}
                        responseExample={entityResponseExample(operationName, entity, apiDescription, instanceRootId)}
                        apiBaseUrl={apiBaseUrl}
                      />
                    ))}
                  </div>
                </article>
              ))}
            </section>

            <section className="api-docs-section" aria-labelledby="api-docs-associations">
              <h2 id="api-docs-associations">Associations</h2>
              {(apiDescription.associations?.length ?? 0) === 0 ? (
                <div className="tree-empty">No association types</div>
              ) : apiDescription.associations?.map((association) => (
                <article key={association.azName} className="api-docs-type">
                  <header>
                    <h3>{association.visName}</h3>
                    <span>{association.azName} · {association.kind}</span>
                  </header>
                  <div className="api-docs-fields">
                    <span>
                      <strong>Source</strong>
                      {association.sourceEntityAzName}{association.sourceRoleName ? ` · ${association.sourceRoleName}` : ""}
                    </span>
                    <span>
                      <strong>Target</strong>
                      {association.targetEntityAzName}{association.targetRoleName ? ` · ${association.targetRoleName}` : ""}
                    </span>
                    {association.cardinality && (
                      <span>
                        <strong>Cardinality</strong>
                        {association.cardinality}
                      </span>
                    )}
                  </div>
                  <div className="api-docs-operations">
                    {Object.entries(association.linkOperations ?? {}).map(([operationName, pathTemplate]) => (
                      <ApiOperation
                        key={`${association.azName}-${operationName}`}
                        method={methodForAssociationOperation(operationName)}
                        name={operationName}
                        path={resolvedApiPath(pathTemplate, apiDescription.modelAzName, instanceRootId)}
                        purpose={associationOperationPurpose(operationName, association)}
                        requestExample={operationName === "create" ? associationBodyExample(association) : null}
                        responseExample={associationResponseExample(operationName, association, apiDescription)}
                        apiBaseUrl={apiBaseUrl}
                      />
                    ))}
                  </div>
                </article>
              ))}
            </section>
          </>
        )}
      </section>
    </main>
  );
}

function ApiOperation({
  method,
  name,
  path,
  purpose,
  requestExample,
  responseExample,
  apiBaseUrl,
}: {
  method: string;
  name: string;
  path: string;
  purpose: string;
  requestExample: unknown;
  responseExample: unknown;
  apiBaseUrl: string;
}) {
  const needsInstanceId = path.includes("{instanceId}");
  const [instanceId, setInstanceId] = useState("");
  const [requestBody, setRequestBody] = useState(formatEditableJson(requestExample));
  const [isExecuting, setIsExecuting] = useState(false);
  const [result, setResult] = useState<TryItResult | null>(null);

  async function executeRequest() {
    const trimmedInstanceId = instanceId.trim();
    if (needsInstanceId && !trimmedInstanceId) {
      setResult({
        method,
        url: absoluteApiUrl(apiBaseUrl, path),
        requestBody: requestExample === null ? "" : requestBody,
        responseBody: "",
        errorMessage: "instanceId is required",
      });
      return;
    }

    const resolvedPath = path.split("{instanceId}").join(encodeURIComponent(trimmedInstanceId));
    const url = absoluteApiUrl(apiBaseUrl, resolvedPath);
    const headers: HeadersInit = {
      Accept: "application/json",
    };
    const init: RequestInit = {
      method,
      headers,
    };

    if (requestExample !== null) {
      try {
        JSON.parse(requestBody);
      } catch (error) {
        setResult({
          method,
          url,
          requestBody,
          responseBody: "",
          errorMessage: error instanceof Error ? error.message : "Request body is not valid JSON",
        });
        return;
      }
      headers["Content-Type"] = "application/json";
      init.body = requestBody;
    }

    setIsExecuting(true);
    setResult(null);
    try {
      const response = await fetch(url, init);
      const responseText = await response.text();
      setResult({
        method,
        url,
        requestBody: requestExample === null ? "" : requestBody,
        statusCode: response.status,
        responseBody: formatResponseBody(responseText),
        errorMessage: response.ok ? undefined : response.statusText || `HTTP ${response.status}`,
      });
    } catch (error) {
      setResult({
        method,
        url,
        requestBody: requestExample === null ? "" : requestBody,
        responseBody: "",
        errorMessage: error instanceof Error ? error.message : "Request failed",
      });
    } finally {
      setIsExecuting(false);
    }
  }

  return (
    <details className="api-operation">
      <summary>
        <span className={`api-method api-method-${method.toLocaleLowerCase()}`}>{method}</span>
        <span>{path}</span>
        <strong>{name}</strong>
      </summary>
      <div className="api-operation-body">
        <p>{purpose}</p>
        <div className="api-example-grid">
          <div>
            <h4>Request</h4>
            <pre>{formatJsonExample(requestExample)}</pre>
          </div>
          <div>
            <h4>Response</h4>
            <pre>{formatJsonExample(responseExample)}</pre>
          </div>
        </div>
        <div className="api-try-it">
          <div className="api-try-it-header">
            <h4>Try it</h4>
            <button type="button" onClick={() => void executeRequest()} disabled={isExecuting || !apiBaseUrl}>
              {isExecuting ? "Running" : "Execute"}
            </button>
          </div>
          <div className="api-try-it-fields">
            <label>
              <span>Method</span>
              <input value={method} readOnly />
            </label>
            <label>
              <span>URL</span>
              <input value={absoluteApiUrl(apiBaseUrl, path.split("{instanceId}").join(needsInstanceId ? (instanceId.trim() || "{instanceId}") : ""))} readOnly />
            </label>
            {needsInstanceId && (
              <label>
                <span>instanceId</span>
                <input value={instanceId} onChange={(event) => setInstanceId(event.target.value)} placeholder="00000000-0000-0000-0000-000000000000" />
              </label>
            )}
          </div>
          {requestExample !== null && (
            <label className="api-try-it-body">
              <span>Request body</span>
              <textarea value={requestBody} onChange={(event) => setRequestBody(event.target.value)} spellCheck={false} />
            </label>
          )}
          {result !== null && (
            <div className="api-try-it-result">
              <dl>
                <div>
                  <dt>Request</dt>
                  <dd>{result.method} {result.url}</dd>
                </div>
                <div>
                  <dt>Status</dt>
                  <dd>{result.statusCode ?? "Not sent"}</dd>
                </div>
                {result.errorMessage && (
                  <div>
                    <dt>Error</dt>
                    <dd>{result.errorMessage}</dd>
                  </div>
                )}
              </dl>
              <div>
                <h5>Request body</h5>
                <pre>{result.requestBody || "No request body"}</pre>
              </div>
              <div>
                <h5>Response body</h5>
                <pre>{result.responseBody || "No response body"}</pre>
              </div>
            </div>
          )}
        </div>
      </div>
    </details>
  );
}

function VisualizationWizardPage() {
  const modelAzName = readQueryParam("modelAzName");
  const instanceRootId = readQueryParam("instanceRootId");
  const [apiBaseUrl, setApiBaseUrl] = useState("");
  const [apiDescription, setApiDescription] = useState<ApiDescriptionResponse | null>(null);
  const [root, setRoot] = useState<ModelInstanceRootResponse | null>(null);
  const [status, setStatus] = useState<ModelInstanceLoadState>("loading");
  const [statusMessage, setStatusMessage] = useState("Loading visualization wizard...");
  const [step, setStep] = useState<VisualizationWizardStep>("chartType");
  const [selectedChartTypeId, setSelectedChartTypeId] = useState(TIDY_TREE_CHART_ID);
  const [binding, setBinding] = useState<TidyTreeBinding>({
    rootLabel: "",
    rootSelection: defaultRootSelection(null, null),
    levels: [],
  });
  const [rootMatchState, setRootMatchState] = useState<RootMatchState>({
    status: "idle",
    message: "Manual chart root",
  });
  const [levelOneFilterMatchState, setLevelOneFilterMatchState] = useState<RootMatchState>({
    status: "idle",
    message: "Level 1 filter disabled",
  });
  const [visualizationData, setVisualizationData] = useState<VisualizationDataState>({
    status: "idle",
    message: "Visualization data not loaded",
    tree: null,
  });

  useEffect(() => {
    let cancelled = false;

    async function loadWizard() {
      if (!modelAzName || !instanceRootId) {
        setStatus("error");
        setStatusMessage("Visualization URL is missing modelAzName or instanceRootId");
        return;
      }

      try {
        const config = await loadRuntimeConfig();
        const baseUrl = normalizeBaseUrl(config.apiBaseUrl ?? "");
        if (!baseUrl) {
          throw new Error("Backend URL is not configured");
        }
        const [nextApiDescription, nextRoot] = await Promise.all([
          fetchRootModelInstanceApi(baseUrl, modelAzName, instanceRootId),
          fetchModelInstanceRoot(baseUrl, modelAzName, instanceRootId),
        ]);
        if (cancelled) {
          return;
        }
        const firstEntity = nextApiDescription.entities.find((entity) => traversalOptionsFor(entity, nextApiDescription).length > 0)
          ?? nextApiDescription.entities[0]
          ?? null;
        const firstTraversal = traversalOptionsFor(firstEntity, nextApiDescription)[0] ?? null;
        const defaultLevels: TidyTreeBindingLevel[] = firstEntity === null
          ? []
          : [
              {
                entityAzName: firstEntity.azName,
                labelTemplate: defaultLabelTemplate(firstEntity),
                filter: defaultLevelFilter(firstEntity),
              },
            ];
        if (firstTraversal !== null) {
          defaultLevels.push({
            entityAzName: firstTraversal.relatedEntity.azName,
            labelTemplate: defaultLabelTemplate(firstTraversal.relatedEntity),
            traversal: {
              associationAzName: firstTraversal.association.azName,
              direction: firstTraversal.direction,
            },
          });
        }
        setApiBaseUrl(baseUrl);
        setApiDescription(nextApiDescription);
        setRoot(nextRoot);
        setBinding({
          rootLabel: rootResponseDisplayName(nextRoot),
          rootSelection: defaultRootSelection(firstEntity, nextApiDescription),
          levels: defaultLevels,
        });
        setStatus("ok");
        setStatusMessage("Visualization wizard ready");
      } catch (error) {
        if (!cancelled) {
          setStatus("error");
          setStatusMessage(error instanceof Error ? error.message : "Visualization wizard load failed");
        }
      }
    }

    void loadWizard();

    return () => {
      cancelled = true;
    };
  }, [modelAzName, instanceRootId]);

  useEffect(() => {
    if (binding.rootSelection.mode !== "entity") {
      setRootMatchState({
        status: "idle",
        message: "Manual chart root",
      });
      return;
    }
    if (!apiBaseUrl || apiDescription === null || !modelAzName || !instanceRootId) {
      setRootMatchState({
        status: "idle",
        message: "Root matching unavailable",
      });
      return;
    }
    const validationError = rootSelectionValidationMessage(apiDescription, binding);
    if (validationError !== null) {
      setRootMatchState({
        status: "idle",
        message: validationError,
      });
      return;
    }

    let cancelled = false;
    const timeoutId = window.setTimeout(() => {
      setRootMatchState({
        status: "loading",
        message: "Resolving root match...",
      });
      resolveTidyTreeRootInstances(apiBaseUrl, modelAzName, instanceRootId, apiDescription, binding)
        .then((instances) => {
          if (cancelled) {
            return;
          }
          setRootMatchState({
            status: "ok",
            message: `${instances.length} root match${instances.length === 1 ? "" : "es"}`,
            count: instances.length,
            instance: instances.length === 1 ? instances[0] : undefined,
          });
        })
        .catch((error) => {
          if (cancelled) {
            return;
          }
          setRootMatchState({
            status: "error",
            message: error instanceof Error ? error.message : "Root match failed",
          });
        });
    }, 250);

    return () => {
      cancelled = true;
      window.clearTimeout(timeoutId);
    };
  }, [apiBaseUrl, apiDescription, binding, instanceRootId, modelAzName]);

  useEffect(() => {
    const levelOneFilter = binding.levels[0]?.filter ?? null;
    if (binding.rootSelection.mode !== "manual" || levelOneFilter === null || !levelOneFilter.enabled) {
      setLevelOneFilterMatchState({
        status: "idle",
        message: "Level 1 filter disabled",
      });
      return;
    }
    if (!apiBaseUrl || apiDescription === null || !modelAzName || !instanceRootId) {
      setLevelOneFilterMatchState({
        status: "idle",
        message: "Level 1 filter unavailable",
      });
      return;
    }
    const validationError = levelOneFilterValidationMessage(apiDescription, binding);
    if (validationError !== null) {
      setLevelOneFilterMatchState({
        status: "idle",
        message: validationError,
      });
      return;
    }

    let cancelled = false;
    const timeoutId = window.setTimeout(() => {
      setLevelOneFilterMatchState({
        status: "loading",
        message: "Resolving Level 1 filter...",
      });
      resolveTidyTreeLevelOneFilterInstances(apiBaseUrl, modelAzName, instanceRootId, apiDescription, binding)
        .then((instances) => {
          if (cancelled) {
            return;
          }
          setLevelOneFilterMatchState({
            status: "ok",
            message: `${instances.length} Level 1 match${instances.length === 1 ? "" : "es"}`,
            count: instances.length,
          });
        })
        .catch((error) => {
          if (cancelled) {
            return;
          }
          setLevelOneFilterMatchState({
            status: "error",
            message: error instanceof Error ? error.message : "Level 1 filter failed",
          });
        });
    }, 250);

    return () => {
      cancelled = true;
      window.clearTimeout(timeoutId);
    };
  }, [apiBaseUrl, apiDescription, binding, instanceRootId, modelAzName]);

  const rootName = root === null ? instanceRootId : rootResponseDisplayName(root);
  const chartOptions = useMemo(() => CHART_TYPES.map((chartType) => ({
    chartType,
    eligibility: apiDescription === null
      ? { selectable: false, reason: "Model metadata is not loaded." }
      : chartType.evaluateEligibility(apiDescription),
  })), [apiDescription]);
  const selectedChartType = CHART_TYPES.find((chartType) => chartType.id === selectedChartTypeId) ?? CHART_TYPES[0];
  const selectedChartEligibility = chartOptions.find((option) => option.chartType.id === selectedChartType.id)?.eligibility ?? { selectable: false, reason: "Chart type unavailable." };
  const bindingMessage = bindingValidationMessage(apiDescription, binding, rootMatchState, levelOneFilterMatchState);
  const canContinueToBinding = status === "ok" && selectedChartEligibility.selectable;
  const canRenderVisualization = canContinueToBinding && bindingMessage === null;

  function clearVisualizationData() {
    setVisualizationData({
      status: "idle",
      message: "Visualization data not loaded",
      tree: null,
    });
  }

  function updateRootSelection(nextRootSelection: TidyTreeRootSelection) {
    setBinding((current) => ({
      ...current,
      rootSelection: nextRootSelection,
    }));
    clearVisualizationData();
  }

  function updateBindingLevel(index: number, nextLevel: TidyTreeBindingLevel, truncateFollowingLevels = false) {
    setBinding((current) => ({
      ...current,
      levels: current.levels
        .map((level, levelIndex) => levelIndex === index ? nextLevel : level)
        .slice(0, truncateFollowingLevels ? index + 1 : current.levels.length),
    }));
    clearVisualizationData();
  }

  function selectFirstLevelEntity(entityAzName: string) {
    const entity = findEntity(apiDescription?.entities ?? [], entityAzName);
    setBinding((current) => ({
      ...current,
      rootSelection: defaultRootSelection(entity, apiDescription, current.rootSelection.mode),
      levels: entity === null ? [] : [{
        entityAzName: entity.azName,
        labelTemplate: defaultLabelTemplate(entity),
        filter: defaultLevelFilter(entity),
      }],
    }));
    clearVisualizationData();
  }

  function addNextLevel() {
    if (apiDescription === null || binding.levels.length === 0) {
      return;
    }
    const nextLevelIndex = binding.levels.length;
    const option = traversalOptionsForBindingLevel(apiDescription, binding, nextLevelIndex)[0] ?? null;
    if (option === null) {
      return;
    }
    setBinding((current) => ({
      ...current,
      levels: [
        ...current.levels,
        {
          entityAzName: option.relatedEntity.azName,
          labelTemplate: defaultLabelTemplate(option.relatedEntity),
          traversal: {
            associationAzName: option.association.azName,
            direction: option.direction,
          },
        },
      ],
    }));
    clearVisualizationData();
  }

  function removeLastLevel() {
    setBinding((current) => ({
      ...current,
      levels: current.levels.slice(0, Math.max(1, current.levels.length - 1)),
    }));
    clearVisualizationData();
  }

  async function loadVisualizationData() {
    if (!apiBaseUrl || apiDescription === null || !modelAzName || !instanceRootId) {
      setVisualizationData({
        status: "error",
        message: "Visualization context is incomplete",
        tree: null,
      });
      return;
    }
    const validationError = bindingValidationMessage(apiDescription, binding);
    if (validationError !== null) {
      setVisualizationData({
        status: "error",
        message: validationError,
        tree: null,
      });
      return;
    }

    setVisualizationData({
      status: "loading",
      message: "Loading visualization data...",
      tree: visualizationData.tree,
    });
    try {
      const tree = await buildTidyTreeData(apiBaseUrl, apiDescription.modelAzName, instanceRootId, apiDescription, binding);
      setVisualizationData({
        status: "ok",
        message: `${tree.children.length} top-level node${tree.children.length === 1 ? "" : "s"} rendered`,
        tree,
        loadedAt: new Date().toLocaleTimeString(),
      });
    } catch (error) {
      setVisualizationData({
        status: "error",
        message: error instanceof Error ? error.message : "Visualization data load failed",
        tree: null,
      });
    }
  }

  return (
    <main className="visualize-shell">
      <header className="visualize-header">
        <div>
          <h1>Visualize Model Instance</h1>
          <div className="query-console-targets">
            <span>{apiDescription?.modelVisName ?? modelAzName}</span>
            <span>{rootName}</span>
          </div>
        </div>
        <a className="secondary-link" href="/?tab=modelInstances">
          Model instances
        </a>
      </header>

      <section className="visualize-surface">
        <div className="visualize-steps" aria-label="Visualization wizard steps">
          {(["chartType", "binding", "visualization"] as VisualizationWizardStep[]).map((candidateStep, index) => (
            <button
              key={candidateStep}
              type="button"
              className={step === candidateStep ? "visualize-step visualize-step-active" : "visualize-step"}
              onClick={() => {
                if (candidateStep === "binding" && !canContinueToBinding) {
                  return;
                }
                if (candidateStep === "visualization" && !canRenderVisualization) {
                  return;
                }
                setStep(candidateStep);
              }}
              disabled={(candidateStep === "binding" && !canContinueToBinding) || (candidateStep === "visualization" && !canRenderVisualization)}
            >
              <span>{index + 1}</span>
              {candidateStep === "chartType" ? "Chart type" : candidateStep === "binding" ? "Binding" : "Visualization"}
            </button>
          ))}
        </div>

        <span className={`model-status model-status-${status}`}>{statusMessage}</span>

        {step === "chartType" && (
          <section className="visualize-panel" aria-labelledby="visualize-chart-type">
            <h2 id="visualize-chart-type">Chart Type Selection</h2>
            <div className="chart-type-grid">
              {chartOptions.map(({ chartType, eligibility }) => (
                <button
                  key={chartType.id}
                  type="button"
                  className={selectedChartTypeId === chartType.id ? "chart-type-option chart-type-option-active" : "chart-type-option"}
                  onClick={() => {
                    if (eligibility.selectable) {
                      setSelectedChartTypeId(chartType.id);
                    }
                  }}
                  disabled={!eligibility.selectable}
                >
                  <strong>{chartType.name}</strong>
                  <span>{chartType.summary}</span>
                  {!eligibility.selectable && <em>{eligibility.reason}</em>}
                </button>
              ))}
            </div>
            <div className="visualize-actions">
              <button type="button" onClick={() => setStep("binding")} disabled={!canContinueToBinding}>
                Continue
              </button>
            </div>
          </section>
        )}

        {step === "binding" && (
          <TidyTreeBindingPanel
            apiDescription={apiDescription}
            binding={binding}
            validationMessage={bindingMessage}
            rootMatchState={rootMatchState}
            levelOneFilterMatchState={levelOneFilterMatchState}
            onRootLabelChange={(rootLabel) => {
              setBinding((current) => ({ ...current, rootLabel }));
              clearVisualizationData();
            }}
            onRootSelectionChange={updateRootSelection}
            onFirstEntityChange={selectFirstLevelEntity}
            onLevelChange={updateBindingLevel}
            onAddLevel={addNextLevel}
            onRemoveLastLevel={removeLastLevel}
            onVisualize={() => {
              setStep("visualization");
              void loadVisualizationData();
            }}
          />
        )}

        {step === "visualization" && (
          <section className="visualize-panel visualize-panel-fill" aria-labelledby="visualize-output">
            <div className="visualization-header-row">
              <div>
                <h2 id="visualize-output">{selectedChartType.name}</h2>
                <span className={`model-status model-status-${visualizationData.status}`}>{visualizationData.message}</span>
                {visualizationData.loadedAt && <span className="visualization-loaded-at">Loaded {visualizationData.loadedAt}</span>}
              </div>
              <div className="visualize-actions">
                <button type="button" onClick={() => setStep("binding")}>
                  Binding
                </button>
                <button type="button" onClick={() => void loadVisualizationData()} disabled={!canRenderVisualization || visualizationData.status === "loading"}>
                  Refresh
                </button>
              </div>
            </div>
            <div className="visualization-canvas" aria-label={`${selectedChartType.name} visualization`}>
              {visualizationData.tree === null ? (
                <div className="tree-empty">{visualizationData.status === "loading" ? "Loading tree..." : "No visualization data"}</div>
              ) : selectedChartType.id === TREE_OF_LIFE_CHART_ID ? (
                <TreeOfLifeRenderer tree={visualizationData.tree} />
              ) : selectedChartType.id === RADIAL_TREE_CHART_ID ? (
                <RadialTreeRenderer tree={visualizationData.tree} />
              ) : (
                <TidyTreeRenderer tree={visualizationData.tree} />
              )}
            </div>
          </section>
        )}
      </section>
    </main>
  );
}

function TidyTreeBindingPanel({
  apiDescription,
  binding,
  validationMessage,
  rootMatchState,
  levelOneFilterMatchState,
  onRootLabelChange,
  onRootSelectionChange,
  onFirstEntityChange,
  onLevelChange,
  onAddLevel,
  onRemoveLastLevel,
  onVisualize,
}: {
  apiDescription: ApiDescriptionResponse | null;
  binding: TidyTreeBinding;
  validationMessage: string | null;
  rootMatchState: RootMatchState;
  levelOneFilterMatchState: RootMatchState;
  onRootLabelChange: (value: string) => void;
  onRootSelectionChange: (value: TidyTreeRootSelection) => void;
  onFirstEntityChange: (entityAzName: string) => void;
  onLevelChange: (index: number, level: TidyTreeBindingLevel, truncateFollowingLevels?: boolean) => void;
  onAddLevel: () => void;
  onRemoveLastLevel: () => void;
  onVisualize: () => void;
}) {
  const canAddLevel = apiDescription !== null && traversalOptionsForBindingLevel(apiDescription, binding, binding.levels.length).length > 0;
  const rootEntity = selectedRootEntity(apiDescription, binding);
  const rootRelationshipOptions = traversalOptionsFor(rootEntity, apiDescription);
  const levelOneEntity = findEntity(apiDescription?.entities ?? [], binding.levels[0]?.entityAzName ?? "");
  const levelOneRelationshipOptions = traversalOptionsFor(levelOneEntity, apiDescription);
  const labelInputRefs = useRef(new Map<number, HTMLInputElement>());
  const labelCursorRanges = useRef(new Map<number, { start: number; end: number }>());

  function rememberLabelCursor(index: number, input: HTMLInputElement) {
    labelCursorRanges.current.set(index, {
      start: input.selectionStart ?? input.value.length,
      end: input.selectionEnd ?? input.value.length,
    });
  }

  function insertLabelTemplateHint(index: number, level: TidyTreeBindingLevel, hint: string) {
    const input = labelInputRefs.current.get(index);
    const currentTemplate = level.labelTemplate;
    const cursorRange = input === undefined
      ? labelCursorRanges.current.get(index) ?? { start: currentTemplate.length, end: currentTemplate.length }
      : {
          start: input.selectionStart ?? labelCursorRanges.current.get(index)?.start ?? currentTemplate.length,
          end: input.selectionEnd ?? labelCursorRanges.current.get(index)?.end ?? currentTemplate.length,
        };
    const nextTemplate = `${currentTemplate.slice(0, cursorRange.start)}${hint}${currentTemplate.slice(cursorRange.end)}`;
    const nextCursor = cursorRange.start + hint.length;

    onLevelChange(index, { ...level, labelTemplate: nextTemplate });
    labelCursorRanges.current.set(index, { start: nextCursor, end: nextCursor });

    window.requestAnimationFrame(() => {
      const nextInput = labelInputRefs.current.get(index);
      if (nextInput === undefined) {
        return;
      }
      nextInput.focus();
      nextInput.setSelectionRange(nextCursor, nextCursor);
    });
  }

  function setRootMode(mode: TidyTreeRootMode) {
    onRootSelectionChange({
      ...binding.rootSelection,
      mode,
    });
  }

  function setRootLabelTemplate(labelTemplate: string) {
    onRootSelectionChange({
      ...binding.rootSelection,
      labelTemplate,
    });
  }

  function updateRootDirectCriterion(index: number, criterion: TidyTreeRootDirectCriterion) {
    onRootSelectionChange({
      ...binding.rootSelection,
      directCriteria: binding.rootSelection.directCriteria.map((candidate, candidateIndex) => (
        candidateIndex === index ? criterion : candidate
      )),
    });
  }

  function updateRootRelationshipCriterion(index: number, criterion: TidyTreeRootRelationshipCriterion) {
    onRootSelectionChange({
      ...binding.rootSelection,
      relationshipCriteria: binding.rootSelection.relationshipCriteria.map((candidate, candidateIndex) => (
        candidateIndex === index ? criterion : candidate
      )),
    });
  }

  function updateLevelOneFilter(filter: TidyTreeLevelFilter) {
    const level = binding.levels[0] ?? null;
    if (level === null) {
      return;
    }
    onLevelChange(0, {
      ...level,
      filter,
    }, false);
  }

  function updateLevelOneDirectCriterion(index: number, criterion: TidyTreeRootDirectCriterion) {
    const filter = binding.levels[0]?.filter ?? defaultLevelFilter(levelOneEntity);
    updateLevelOneFilter({
      ...filter,
      directCriteria: filter.directCriteria.map((candidate, candidateIndex) => (
        candidateIndex === index ? criterion : candidate
      )),
    });
  }

  function updateLevelOneRelationshipCriterion(index: number, criterion: TidyTreeRootRelationshipCriterion) {
    const filter = binding.levels[0]?.filter ?? defaultLevelFilter(levelOneEntity);
    updateLevelOneFilter({
      ...filter,
      relationshipCriteria: filter.relationshipCriteria.map((candidate, candidateIndex) => (
        candidateIndex === index ? criterion : candidate
      )),
    });
  }

  return (
    <section className="visualize-panel" aria-labelledby="visualize-binding">
      <h2 id="visualize-binding">Model Element Binding</h2>
      <fieldset className="binding-root-mode">
        <legend>Chart root</legend>
        <label>
          <input
            type="radio"
            name="tidy-tree-root-mode"
            checked={binding.rootSelection.mode === "manual"}
            onChange={() => setRootMode("manual")}
          />
          Write root node title
        </label>
        <label>
          <input
            type="radio"
            name="tidy-tree-root-mode"
            checked={binding.rootSelection.mode === "entity"}
            onChange={() => setRootMode("entity")}
          />
          Select model entity data instance node
        </label>
      </fieldset>

      {binding.rootSelection.mode === "manual" ? (
        <div className="binding-grid">
          <label className="query-field">
            <span>Chart root label</span>
            <input value={binding.rootLabel} onChange={(event) => onRootLabelChange(event.target.value)} />
          </label>
        </div>
      ) : (
        <section className="binding-root-selection" aria-labelledby="tidy-tree-root-selection">
          <h3 id="tidy-tree-root-selection">Root Node Selection</h3>
          <div className="binding-grid binding-grid-two">
            <label className="query-field">
              <span>Entity node type</span>
              <select
                value={binding.levels[0]?.entityAzName ?? ""}
                onChange={(event) => onFirstEntityChange(event.target.value)}
                disabled={apiDescription === null || apiDescription.entities.length === 0}
              >
                {apiDescription === null || apiDescription.entities.length === 0 ? (
                  <option value="">No entity types</option>
                ) : apiDescription.entities.map((entity) => (
                  <option key={entity.azName} value={entity.azName}>
                    {entity.visName} ({entity.azName})
                  </option>
                ))}
              </select>
            </label>
            <label className="query-field">
              <span>Root label template</span>
              <input
                value={binding.rootSelection.labelTemplate}
                placeholder={rootEntity === null ? "{id}" : defaultLabelTemplate(rootEntity)}
                onChange={(event) => setRootLabelTemplate(event.target.value)}
              />
            </label>
          </div>

          {rootEntity !== null && rootEntity.attributes.length > 0 && (
            <div className="binding-template-hints" aria-label="Root label template hints">
              {[...rootEntity.attributes.map((attribute) => `{${attribute.azName}}`), "{id}"].map((hint) => (
                <button
                  key={hint}
                  type="button"
                  onClick={() => setRootLabelTemplate(`${binding.rootSelection.labelTemplate}${hint}`)}
                  title={`Append ${hint}`}
                >
                  <code>{hint}</code>
                </button>
              ))}
            </div>
          )}

          <div className="binding-criteria">
            <header>
              <h4>Comparisons</h4>
              <button
                type="button"
                onClick={() => onRootSelectionChange({
                  ...binding.rootSelection,
                  directCriteria: [
                    ...binding.rootSelection.directCriteria,
                    defaultRootDirectCriterion(rootEntity),
                  ],
                })}
                disabled={rootEntity === null || rootEntity.attributes.length === 0}
              >
                Add comparison
              </button>
            </header>
            {binding.rootSelection.directCriteria.map((criterion, index) => {
              const attribute = rootEntity?.attributes.find((candidate) => candidate.azName === criterion.attributeAzName) ?? rootEntity?.attributes[0] ?? null;
              const operators = queryOperatorsFor(attribute);
              return (
                <div key={`root-direct-${index}`} className="binding-criterion-row">
                  <label className="query-field">
                    <span>Attribute</span>
                    <select
                      value={attribute?.azName ?? ""}
                      onChange={(event) => {
                        const nextAttribute = rootEntity?.attributes.find((candidate) => candidate.azName === event.target.value) ?? null;
                        updateRootDirectCriterion(index, {
                          ...criterion,
                          attributeAzName: event.target.value,
                          operator: queryOperatorsFor(nextAttribute)[0],
                        });
                      }}
                      disabled={rootEntity === null || rootEntity.attributes.length === 0}
                    >
                      {rootEntity === null || rootEntity.attributes.length === 0 ? (
                        <option value="">No attributes</option>
                      ) : rootEntity.attributes.map((candidate) => (
                        <option key={candidate.azName} value={candidate.azName}>
                          {candidate.visName} ({candidate.azName})
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="query-field query-field-operator">
                    <span>Operator</span>
                    <select
                      value={operators.includes(criterion.operator) ? criterion.operator : operators[0]}
                      onChange={(event) => updateRootDirectCriterion(index, { ...criterion, operator: event.target.value as QueryOperator })}
                      disabled={attribute === null}
                    >
                      {operators.map((operator) => (
                        <option key={operator} value={operator}>{operator}</option>
                      ))}
                    </select>
                  </label>
                  <label className="query-field">
                    <span>Value</span>
                    <input
                      value={criterion.value}
                      type={inputTypeFor(attribute)}
                      step={inputStepFor(attribute)}
                      onChange={(event) => updateRootDirectCriterion(index, { ...criterion, value: event.target.value })}
                      disabled={attribute === null}
                    />
                  </label>
                  <button
                    type="button"
                    className="binding-remove-button"
                    onClick={() => onRootSelectionChange({
                      ...binding.rootSelection,
                      directCriteria: binding.rootSelection.directCriteria.filter((_candidate, candidateIndex) => candidateIndex !== index),
                    })}
                    disabled={binding.rootSelection.directCriteria.length <= 1}
                  >
                    Remove
                  </button>
                </div>
              );
            })}
          </div>

          <div className="binding-criteria">
            <header>
              <h4>Relationship Criteria</h4>
              <button
                type="button"
                onClick={() => onRootSelectionChange({
                  ...binding.rootSelection,
                  relationshipCriteria: [
                    ...binding.rootSelection.relationshipCriteria,
                    defaultRootRelationshipCriterion(rootEntity, apiDescription),
                  ],
                })}
                disabled={rootRelationshipOptions.length === 0}
              >
                Add relationship
              </button>
            </header>
            {binding.rootSelection.relationshipCriteria.length === 0 ? (
              <div className="tree-empty">No relationship criteria</div>
            ) : binding.rootSelection.relationshipCriteria.map((criterion, index) => {
              const traversal = selectedRelationshipTraversal(apiDescription, rootEntity, criterion) ?? rootRelationshipOptions[0] ?? null;
              const relatedAttribute = traversal?.relatedEntity.attributes.find((candidate) => candidate.azName === criterion.relatedAttributeAzName)
                ?? traversal?.relatedEntity.attributes[0]
                ?? null;
              const operators = queryOperatorsFor(relatedAttribute);
              return (
                <div key={`root-relationship-${index}`} className="binding-criterion-row binding-criterion-row-wide">
                  <label className="query-field query-field-wide">
                    <span>Association</span>
                    <select
                      value={traversal === null ? "" : traversalOptionValue(traversal)}
                      onChange={(event) => {
                        const nextTraversal = rootRelationshipOptions.find((option) => traversalOptionValue(option) === event.target.value) ?? null;
                        updateRootRelationshipCriterion(index, {
                          ...criterion,
                          traversalValue: event.target.value,
                          relatedAttributeAzName: nextTraversal?.relatedEntity.attributes[0]?.azName ?? "",
                          operator: "=",
                        });
                      }}
                      disabled={rootRelationshipOptions.length === 0}
                    >
                      {rootRelationshipOptions.length === 0 ? (
                        <option value="">No traversable associations</option>
                      ) : rootRelationshipOptions.map((option) => (
                        <option key={traversalOptionValue(option)} value={traversalOptionValue(option)}>
                          {traversalLabel(option)}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="query-field">
                    <span>Related attribute</span>
                    <select
                      value={relatedAttribute?.azName ?? ""}
                      onChange={(event) => {
                        const nextAttribute = traversal?.relatedEntity.attributes.find((candidate) => candidate.azName === event.target.value) ?? null;
                        updateRootRelationshipCriterion(index, {
                          ...criterion,
                          relatedAttributeAzName: event.target.value,
                          operator: queryOperatorsFor(nextAttribute)[0],
                        });
                      }}
                      disabled={traversal === null || traversal.relatedEntity.attributes.length === 0}
                    >
                      {traversal === null || traversal.relatedEntity.attributes.length === 0 ? (
                        <option value="">No related attributes</option>
                      ) : traversal.relatedEntity.attributes.map((candidate) => (
                        <option key={candidate.azName} value={candidate.azName}>
                          {candidate.visName} ({candidate.azName})
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="query-field query-field-operator">
                    <span>Operator</span>
                    <select
                      value={operators.includes(criterion.operator) ? criterion.operator : operators[0]}
                      onChange={(event) => updateRootRelationshipCriterion(index, { ...criterion, operator: event.target.value as QueryOperator })}
                      disabled={relatedAttribute === null}
                    >
                      {operators.map((operator) => (
                        <option key={operator} value={operator}>{operator}</option>
                      ))}
                    </select>
                  </label>
                  <label className="query-field">
                    <span>Value</span>
                    <input
                      value={criterion.value}
                      type={inputTypeFor(relatedAttribute)}
                      step={inputStepFor(relatedAttribute)}
                      onChange={(event) => updateRootRelationshipCriterion(index, { ...criterion, value: event.target.value })}
                      disabled={relatedAttribute === null}
                    />
                  </label>
                  <button
                    type="button"
                    className="binding-remove-button"
                    onClick={() => onRootSelectionChange({
                      ...binding.rootSelection,
                      relationshipCriteria: binding.rootSelection.relationshipCriteria.filter((_candidate, candidateIndex) => candidateIndex !== index),
                    })}
                  >
                    Remove
                  </button>
                </div>
              );
            })}
          </div>

          <footer className={`binding-root-match binding-root-match-${rootMatchState.status}`}>
            {rootMatchState.count === undefined ? rootMatchState.message : `Matched root instances: ${rootMatchState.count}`}
          </footer>
        </section>
      )}

      <div className="binding-levels">
        {binding.levels.length === 0 ? (
          <div className="tree-empty">No entity levels</div>
        ) : binding.levels.map((level, index) => {
          const entity = findEntity(apiDescription?.entities ?? [], level.entityAzName);
          const traversalOptions = traversalOptionsForBindingLevel(apiDescription, binding, index);
          const selectedTraversalValue = level.traversal === undefined
            ? ""
            : `${level.traversal.associationAzName}::${level.traversal.direction}::${level.entityAzName}`;
          return (
            <section key={`${index}-${level.entityAzName}`} className="binding-level">
              <header>
                <h3>Level {index + 1}</h3>
                {entity && <span>{entity.visName}</span>}
              </header>
              {index === 0 && binding.rootSelection.mode === "entity" ? (
                <div className="binding-level-root-note">
                  Root entity selected above
                </div>
              ) : index === 0 ? (
                <label className="query-field">
                  <span>Entity</span>
                  <select value={level.entityAzName} onChange={(event) => onFirstEntityChange(event.target.value)}>
                    {apiDescription?.entities.map((candidate) => (
                      <option key={candidate.azName} value={candidate.azName}>
                        {candidate.visName} ({candidate.azName})
                      </option>
                    ))}
                  </select>
                </label>
              ) : (
                <label className="query-field query-field-wide">
                  <span>Association</span>
                  <select
                    value={selectedTraversalValue}
                    onChange={(event) => {
                      const option = traversalOptions.find((candidate) => traversalOptionValue(candidate) === event.target.value) ?? null;
                      if (option !== null) {
                        onLevelChange(index, {
                          entityAzName: option.relatedEntity.azName,
                          labelTemplate: defaultLabelTemplate(option.relatedEntity),
                          traversal: {
                            associationAzName: option.association.azName,
                            direction: option.direction,
                          },
                        }, true);
                      }
                    }}
                  >
                    {traversalOptions.length === 0 ? (
                      <option value="">No acyclic associations</option>
                    ) : traversalOptions.map((option) => (
                      <option key={traversalOptionValue(option)} value={traversalOptionValue(option)}>
                        {traversalLabel(option)}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              {index === 0 && binding.rootSelection.mode === "entity" ? null : (
                <label className="query-field">
                  <span>Label template</span>
                  <input
                    ref={(input) => {
                      if (input === null) {
                        labelInputRefs.current.delete(index);
                      } else {
                        labelInputRefs.current.set(index, input);
                      }
                    }}
                    value={level.labelTemplate}
                    placeholder={entity === null ? "{id}" : defaultLabelTemplate(entity)}
                    onChange={(event) => {
                      rememberLabelCursor(index, event.target);
                      onLevelChange(index, { ...level, labelTemplate: event.target.value });
                    }}
                    onClick={(event) => rememberLabelCursor(index, event.currentTarget)}
                    onFocus={(event) => rememberLabelCursor(index, event.currentTarget)}
                    onKeyUp={(event) => rememberLabelCursor(index, event.currentTarget)}
                    onSelect={(event) => rememberLabelCursor(index, event.currentTarget)}
                  />
                </label>
              )}
              {entity !== null && entity.attributes.length > 0 && !(index === 0 && binding.rootSelection.mode === "entity") && (
                <div className="binding-template-hints" aria-label={`Level ${index + 1} label template hints`}>
                  {[...entity.attributes.map((attribute) => `{${attribute.azName}}`), "{id}"].map((hint) => (
                    <button
                      key={hint}
                      type="button"
                      onPointerDown={(event: PointerEvent<HTMLButtonElement>) => event.preventDefault()}
                      onClick={() => insertLabelTemplateHint(index, level, hint)}
                      title={`Insert ${hint}`}
                    >
                      <code>{hint}</code>
                    </button>
                  ))}
                </div>
              )}
              {index === 0 && binding.rootSelection.mode === "manual" && entity !== null && (
                <section className="binding-criteria binding-level-filter" aria-label="Level 1 filter">
                  <label className="query-criterion-toggle">
                    <input
                      type="checkbox"
                      checked={level.filter?.enabled ?? false}
                      onChange={(event) => {
                        const filter = level.filter ?? defaultLevelFilter(entity);
                        updateLevelOneFilter({
                          ...filter,
                          enabled: event.target.checked,
                        });
                      }}
                    />
                    Filter Level 1 nodes
                  </label>
                  {level.filter?.enabled && (
                    <>
                      <div className="binding-criteria">
                        <header>
                          <h4>Level 1 Comparisons</h4>
                          <button
                            type="button"
                            onClick={() => {
                              const filter = level.filter ?? defaultLevelFilter(entity);
                              updateLevelOneFilter({
                                ...filter,
                                directCriteria: [
                                  ...filter.directCriteria,
                                  defaultRootDirectCriterion(entity),
                                ],
                              });
                            }}
                            disabled={entity.attributes.length === 0}
                          >
                            Add comparison
                          </button>
                        </header>
                        {(level.filter?.directCriteria ?? []).length === 0 ? (
                          <div className="tree-empty">No scalar comparisons</div>
                        ) : (level.filter?.directCriteria ?? []).map((criterion, criterionIndex) => {
                          const attribute = entity.attributes.find((candidate) => candidate.azName === criterion.attributeAzName) ?? entity.attributes[0] ?? null;
                          const operators = queryOperatorsFor(attribute);
                          return (
                            <div key={`level-one-direct-${criterionIndex}`} className="binding-criterion-row">
                              <label className="query-field">
                                <span>Attribute</span>
                                <select
                                  value={attribute?.azName ?? ""}
                                  onChange={(event) => {
                                    const nextAttribute = entity.attributes.find((candidate) => candidate.azName === event.target.value) ?? null;
                                    updateLevelOneDirectCriterion(criterionIndex, {
                                      ...criterion,
                                      attributeAzName: event.target.value,
                                      operator: queryOperatorsFor(nextAttribute)[0],
                                    });
                                  }}
                                  disabled={entity.attributes.length === 0}
                                >
                                  {entity.attributes.length === 0 ? (
                                    <option value="">No attributes</option>
                                  ) : entity.attributes.map((candidate) => (
                                    <option key={candidate.azName} value={candidate.azName}>
                                      {candidate.visName} ({candidate.azName})
                                    </option>
                                  ))}
                                </select>
                              </label>
                              <label className="query-field query-field-operator">
                                <span>Operator</span>
                                <select
                                  value={operators.includes(criterion.operator) ? criterion.operator : operators[0]}
                                  onChange={(event) => updateLevelOneDirectCriterion(criterionIndex, { ...criterion, operator: event.target.value as QueryOperator })}
                                  disabled={attribute === null}
                                >
                                  {operators.map((operator) => (
                                    <option key={operator} value={operator}>{operator}</option>
                                  ))}
                                </select>
                              </label>
                              <label className="query-field">
                                <span>Value</span>
                                <input
                                  value={criterion.value}
                                  type={inputTypeFor(attribute)}
                                  step={inputStepFor(attribute)}
                                  onChange={(event) => updateLevelOneDirectCriterion(criterionIndex, { ...criterion, value: event.target.value })}
                                  disabled={attribute === null}
                                />
                              </label>
                              <button
                                type="button"
                                className="binding-remove-button"
                                onClick={() => {
                                  const filter = level.filter ?? defaultLevelFilter(entity);
                                  updateLevelOneFilter({
                                    ...filter,
                                    directCriteria: filter.directCriteria.filter((_candidate, candidateIndex) => candidateIndex !== criterionIndex),
                                  });
                                }}
                                disabled={(level.filter?.directCriteria.length ?? 0) <= 1 && (level.filter?.relationshipCriteria.length ?? 0) === 0}
                              >
                                Remove
                              </button>
                            </div>
                          );
                        })}
                      </div>

                      <div className="binding-criteria">
                        <header>
                          <h4>Level 1 Relationship Criteria</h4>
                          <button
                            type="button"
                            onClick={() => {
                              const filter = level.filter ?? defaultLevelFilter(entity);
                              updateLevelOneFilter({
                                ...filter,
                                relationshipCriteria: [
                                  ...filter.relationshipCriteria,
                                  defaultRootRelationshipCriterion(entity, apiDescription),
                                ],
                              });
                            }}
                            disabled={levelOneRelationshipOptions.length === 0}
                          >
                            Add relationship
                          </button>
                        </header>
                        {(level.filter?.relationshipCriteria ?? []).length === 0 ? (
                          <div className="tree-empty">No relationship criteria</div>
                        ) : (level.filter?.relationshipCriteria ?? []).map((criterion, criterionIndex) => {
                          const traversal = selectedRelationshipTraversal(apiDescription, entity, criterion) ?? levelOneRelationshipOptions[0] ?? null;
                          const relatedAttribute = traversal?.relatedEntity.attributes.find((candidate) => candidate.azName === criterion.relatedAttributeAzName)
                            ?? traversal?.relatedEntity.attributes[0]
                            ?? null;
                          const operators = queryOperatorsFor(relatedAttribute);
                          return (
                            <div key={`level-one-relationship-${criterionIndex}`} className="binding-criterion-row binding-criterion-row-wide">
                              <label className="query-field query-field-wide">
                                <span>Association</span>
                                <select
                                  value={traversal === null ? "" : traversalOptionValue(traversal)}
                                  onChange={(event) => {
                                    const nextTraversal = levelOneRelationshipOptions.find((option) => traversalOptionValue(option) === event.target.value) ?? null;
                                    updateLevelOneRelationshipCriterion(criterionIndex, {
                                      ...criterion,
                                      traversalValue: event.target.value,
                                      relatedAttributeAzName: nextTraversal?.relatedEntity.attributes[0]?.azName ?? "",
                                      operator: "=",
                                    });
                                  }}
                                  disabled={levelOneRelationshipOptions.length === 0}
                                >
                                  {levelOneRelationshipOptions.length === 0 ? (
                                    <option value="">No traversable associations</option>
                                  ) : levelOneRelationshipOptions.map((option) => (
                                    <option key={traversalOptionValue(option)} value={traversalOptionValue(option)}>
                                      {traversalLabel(option)}
                                    </option>
                                  ))}
                                </select>
                              </label>
                              <label className="query-field">
                                <span>Related attribute</span>
                                <select
                                  value={relatedAttribute?.azName ?? ""}
                                  onChange={(event) => {
                                    const nextAttribute = traversal?.relatedEntity.attributes.find((candidate) => candidate.azName === event.target.value) ?? null;
                                    updateLevelOneRelationshipCriterion(criterionIndex, {
                                      ...criterion,
                                      relatedAttributeAzName: event.target.value,
                                      operator: queryOperatorsFor(nextAttribute)[0],
                                    });
                                  }}
                                  disabled={traversal === null || traversal.relatedEntity.attributes.length === 0}
                                >
                                  {traversal === null || traversal.relatedEntity.attributes.length === 0 ? (
                                    <option value="">No related attributes</option>
                                  ) : traversal.relatedEntity.attributes.map((candidate) => (
                                    <option key={candidate.azName} value={candidate.azName}>
                                      {candidate.visName} ({candidate.azName})
                                    </option>
                                  ))}
                                </select>
                              </label>
                              <label className="query-field query-field-operator">
                                <span>Operator</span>
                                <select
                                  value={operators.includes(criterion.operator) ? criterion.operator : operators[0]}
                                  onChange={(event) => updateLevelOneRelationshipCriterion(criterionIndex, { ...criterion, operator: event.target.value as QueryOperator })}
                                  disabled={relatedAttribute === null}
                                >
                                  {operators.map((operator) => (
                                    <option key={operator} value={operator}>{operator}</option>
                                  ))}
                                </select>
                              </label>
                              <label className="query-field">
                                <span>Value</span>
                                <input
                                  value={criterion.value}
                                  type={inputTypeFor(relatedAttribute)}
                                  step={inputStepFor(relatedAttribute)}
                                  onChange={(event) => updateLevelOneRelationshipCriterion(criterionIndex, { ...criterion, value: event.target.value })}
                                  disabled={relatedAttribute === null}
                                />
                              </label>
                              <button
                                type="button"
                                className="binding-remove-button"
                                onClick={() => {
                                  const filter = level.filter ?? defaultLevelFilter(entity);
                                  updateLevelOneFilter({
                                    ...filter,
                                    relationshipCriteria: filter.relationshipCriteria.filter((_candidate, candidateIndex) => candidateIndex !== criterionIndex),
                                  });
                                }}
                              >
                                Remove
                              </button>
                            </div>
                          );
                        })}
                      </div>

                      <footer className={`binding-root-match binding-root-match-${levelOneFilterMatchState.status}`}>
                        {levelOneFilterMatchState.count === undefined
                          ? levelOneFilterMatchState.message
                          : `Matched Level 1 instances: ${levelOneFilterMatchState.count}`}
                      </footer>
                    </>
                  )}
                </section>
              )}
            </section>
          );
        })}
      </div>

      {validationMessage && <span className="dialog-error">{validationMessage}</span>}

      <div className="visualize-actions">
        <button type="button" onClick={onAddLevel} disabled={!canAddLevel}>
          Add level
        </button>
        <button type="button" onClick={onRemoveLastLevel} disabled={binding.levels.length <= 1}>
          Remove last level
        </button>
        <button type="button" onClick={onVisualize} disabled={validationMessage !== null}>
          Visualize
        </button>
      </div>
    </section>
  );
}

function TidyTreeRenderer({ tree }: { tree: TidyTreeNode }) {
  const svgRef = useRef<SVGSVGElement>(null);

  useEffect(() => {
    const svgElement = svgRef.current;
    if (svgElement === null) {
      return;
    }

    const root = d3.hierarchy<TidyTreeNode>(tree);
    const nodeCount = root.descendants().length;
    const width = Math.max(960, root.height * 260 + 320);
    const height = Math.max(520, nodeCount * 34);
    const treeLayout = d3.tree<TidyTreeNode>().nodeSize([34, 220]);
    treeLayout(root);

    const nodes = root.descendants();
    const minX = Math.min(...nodes.map((node) => node.x ?? 0));
    const maxX = Math.max(...nodes.map((node) => node.x ?? 0));
    const actualHeight = Math.max(height, maxX - minX + 96);
    const offsetX = 96 - minX;

    const svg = d3.select(svgElement);
    svg.selectAll("*").remove();
    svg
      .attr("viewBox", `0 0 ${width} ${actualHeight}`)
      .attr("width", width)
      .attr("height", actualHeight);

    const group = svg.append("g")
      .attr("transform", `translate(72,${offsetX})`);

    group.append("g")
      .attr("class", "tidy-tree-links")
      .selectAll("path")
      .data(root.links())
      .join("path")
      .attr("d", (link) => {
        const sourceX = link.source.x ?? 0;
        const sourceY = link.source.y ?? 0;
        const targetX = link.target.x ?? 0;
        const targetY = link.target.y ?? 0;
        const midY = (sourceY + targetY) / 2;
        return `M${sourceY},${sourceX}C${midY},${sourceX} ${midY},${targetX} ${targetY},${targetX}`;
      });

    const nodeGroup = group.append("g")
      .attr("class", "tidy-tree-nodes")
      .selectAll("g")
      .data(nodes)
      .join("g")
      .attr("transform", (node) => `translate(${node.y ?? 0},${node.x ?? 0})`);

    nodeGroup.append("circle")
      .attr("r", 5.5);

    nodeGroup.append("text")
      .attr("x", (node) => node.children ? -12 : 12)
      .attr("dy", "-0.15em")
      .attr("text-anchor", (node) => node.children ? "end" : "start")
      .text((node) => node.data.label);

    nodeGroup.append("text")
      .attr("class", "tidy-tree-detail")
      .attr("x", (node) => node.children ? -12 : 12)
      .attr("dy", "1.15em")
      .attr("text-anchor", (node) => node.children ? "end" : "start")
      .text((node) => node.data.detail ?? "");
  }, [tree]);

  return <svg ref={svgRef} className="tidy-tree-svg" role="img" aria-label="Tidy tree" />;
}

function RadialTreeRenderer({ tree }: { tree: TidyTreeNode }) {
  const svgRef = useRef<SVGSVGElement>(null);

  useEffect(() => {
    const svgElement = svgRef.current;
    if (svgElement === null) {
      return;
    }

    const hierarchy = d3.hierarchy<TidyTreeNode>(tree)
      .sort((left, right) => left.data.label.localeCompare(right.data.label));
    const radius = Math.max(210, hierarchy.height * 150 + 90);
    const root = d3.tree<TidyTreeNode>()
      .size([2 * Math.PI, radius])
      .separation((left, right) => (left.parent === right.parent ? 1 : 2) / Math.max(1, left.depth))
      (hierarchy);
    const layoutRadius = Math.max(...root.descendants().map((node) => node.y ?? 0));
    const width = Math.max(720, layoutRadius * 2 + 360);
    const height = width;
    const centerX = width * 0.5;
    const centerY = height * 0.52;
    const linkGenerator = d3.linkRadial<d3.HierarchyPointLink<TidyTreeNode>, d3.HierarchyPointNode<TidyTreeNode>>()
      .angle((node) => node.x)
      .radius((node) => node.y);

    const svg = d3.select(svgElement);
    svg.selectAll("*").remove();
    svg
      .attr("viewBox", `${-centerX} ${-centerY} ${width} ${height}`)
      .attr("width", width)
      .attr("height", height);

    svg.append("g")
      .attr("class", "tidy-tree-links")
      .attr("fill", "none")
      .selectAll("path")
      .data(root.links())
      .join("path")
      .attr("d", (link) => linkGenerator(link));

    const nodeGroup = svg.append("g")
      .attr("class", "tidy-tree-nodes")
      .selectAll("g")
      .data(root.descendants())
      .join("g")
      .attr("transform", (node) => `rotate(${node.x * 180 / Math.PI - 90}) translate(${node.y},0)`);

    nodeGroup.append("circle")
      .attr("r", 5);

    nodeGroup.append("title")
      .text((node) => node.data.detail === undefined ? node.data.label : `${node.data.label} - ${node.data.detail}`);

    nodeGroup.append("text")
      .attr("transform", (node) => node.x >= Math.PI ? "rotate(180)" : null)
      .attr("x", (node) => radialLabelGoesOutward(node) ? 11 : -11)
      .attr("dy", "-0.15em")
      .attr("text-anchor", (node) => radialLabelGoesOutward(node) ? "start" : "end")
      .text((node) => node.data.label);

    nodeGroup.append("text")
      .attr("class", "tidy-tree-detail")
      .attr("transform", (node) => node.x >= Math.PI ? "rotate(180)" : null)
      .attr("x", (node) => radialLabelGoesOutward(node) ? 11 : -11)
      .attr("dy", "1.15em")
      .attr("text-anchor", (node) => radialLabelGoesOutward(node) ? "start" : "end")
      .text((node) => node.data.detail ?? "");
  }, [tree]);

  return <svg ref={svgRef} className="tidy-tree-svg" role="img" aria-label="Radial tree" />;
}

function radialLabelGoesOutward(node: d3.HierarchyPointNode<TidyTreeNode>): boolean {
  return node.x < Math.PI === !node.children;
}

function TreeOfLifeRenderer({ tree }: { tree: TidyTreeNode }) {
  const svgRef = useRef<SVGSVGElement>(null);

  useEffect(() => {
    const svgElement = svgRef.current;
    if (svgElement === null) {
      return;
    }

    const hierarchy = d3.hierarchy<TidyTreeNode>(tree)
      .sum((node) => node.children.length === 0 ? 1 : 0)
      .sort((left, right) => d3.ascending(left.height, right.height) || left.data.label.localeCompare(right.data.label));
    const leafCount = Math.max(1, hierarchy.leaves().length);
    const innerRadius = Math.max(250, hierarchy.height * 130 + 80, leafCount * 8);
    const labelRadius = innerRadius + 8;
    const labelAllowance = 250;
    const width = Math.max(760, (innerRadius + labelAllowance) * 2);
    const color = d3.scaleOrdinal<string>()
      .domain((hierarchy.children ?? []).map((node) => node.data.label))
      .range(d3.schemeTableau10);
    const root = d3.cluster<TidyTreeNode>()
      .size([2 * Math.PI, innerRadius])
      .separation(() => 1)
      (hierarchy);
    const nodes = root.descendants();
    const leaves = root.leaves();

    const svg = d3.select(svgElement);
    svg.selectAll("*").remove();
    svg
      .attr("viewBox", `${-width / 2} ${-width / 2} ${width} ${width}`)
      .attr("width", width)
      .attr("height", width);

    svg.append("g")
      .attr("class", "tree-of-life-link-extensions")
      .selectAll("path")
      .data(leaves)
      .join("path")
      .attr("d", (node) => {
        const [x1, y1] = radialPoint(node.x, node.y);
        const [x2, y2] = radialPoint(node.x, innerRadius);
        return `M${x1},${y1}L${x2},${y2}`;
      });

    svg.append("g")
      .attr("class", "tree-of-life-links")
      .selectAll("path")
      .data(root.links())
      .join("path")
      .attr("stroke", (link) => treeOfLifeNodeColor(link.target, color))
      .attr("d", treeOfLifeLinkPath);

    const nodeGroup = svg.append("g")
      .attr("class", "tree-of-life-nodes")
      .selectAll("g")
      .data(nodes)
      .join("g")
      .attr("transform", (node) => `rotate(${node.x * 180 / Math.PI - 90}) translate(${node.y},0)`);

    nodeGroup.append("circle")
      .attr("r", (node) => node.depth === 0 ? 5 : 3.5)
      .attr("fill", (node) => treeOfLifeNodeColor(node, color));

    nodeGroup.append("title")
      .text((node) => node.ancestors().reverse().map((ancestor) => ancestor.data.label).join(" / "));

    svg.append("text")
      .attr("class", "tree-of-life-root-label")
      .attr("text-anchor", "middle")
      .attr("dy", "-0.85em")
      .text(root.data.label);

    svg.append("g")
      .attr("class", "tree-of-life-labels")
      .selectAll("text")
      .data(nodes.filter((node) => node.depth > 0))
      .join("text")
      .attr("dy", "0.31em")
      .attr("transform", (node) => `rotate(${node.x * 180 / Math.PI - 90}) translate(${treeOfLifeLabelRadius(node, labelRadius)},0)${node.x >= Math.PI ? " rotate(180)" : ""}`)
      .attr("text-anchor", (node) => node.x < Math.PI ? "start" : "end")
      .attr("fill", (node) => treeOfLifeNodeColor(node, color))
      .text((node) => node.data.label)
      .append("title")
      .text((node) => node.ancestors().reverse().map((ancestor) => ancestor.data.label).join(" / "));
  }, [tree]);

  return <svg ref={svgRef} className="tidy-tree-svg" role="img" aria-label="Tree of life" />;
}

function radialPoint(angle: number, radius: number): [number, number] {
  const adjustedAngle = angle - Math.PI / 2;
  return [Math.cos(adjustedAngle) * radius, Math.sin(adjustedAngle) * radius];
}

function treeOfLifeLinkPath(link: d3.HierarchyPointLink<TidyTreeNode>): string {
  const sourceAngle = link.source.x;
  const sourceRadius = link.source.y;
  const targetAngle = link.target.x;
  const targetRadius = link.target.y;
  const [sourceX, sourceY] = radialPoint(sourceAngle, sourceRadius);
  const [cornerX, cornerY] = radialPoint(targetAngle, sourceRadius);
  const [targetX, targetY] = radialPoint(targetAngle, targetRadius);
  if (sourceRadius === 0) {
    return `M${sourceX},${sourceY}L${targetX},${targetY}`;
  }
  const largeArcFlag = Math.abs(targetAngle - sourceAngle) > Math.PI ? 1 : 0;
  const sweepFlag = targetAngle > sourceAngle ? 1 : 0;
  return [
    `M${sourceX},${sourceY}`,
    `A${sourceRadius},${sourceRadius} 0 ${largeArcFlag},${sweepFlag} ${cornerX},${cornerY}`,
    `L${targetX},${targetY}`,
  ].join("");
}

function treeOfLifeNodeColor(
  node: d3.HierarchyPointNode<TidyTreeNode>,
  color: d3.ScaleOrdinal<string, string>,
): string {
  const topLevelAncestor = node.ancestors().find((ancestor) => ancestor.depth === 1);
  return topLevelAncestor === undefined ? "#1f2937" : color(topLevelAncestor.data.label);
}

function treeOfLifeLabelRadius(node: d3.HierarchyPointNode<TidyTreeNode>, leafLabelRadius: number): number {
  return node.children === undefined ? leafLabelRadius : node.y + 10;
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
      const valueError = criterionValueError(selectedAttribute, criterionValue);
      if (valueError !== null) {
        setStatus("error");
        setStatusMessage(`Direct criterion ${valueError}`);
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
        const valueError = criterionValueError(selectedRelatedAttribute, relationshipCriterionValue);
        if (valueError !== null) {
          setStatus("error");
          setStatusMessage(`Association criterion ${valueError}`);
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
              type={inputTypeFor(selectedAttribute)}
              step={inputStepFor(selectedAttribute)}
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
              type={relationshipCriterionValue.trim() === "*" ? "text" : inputTypeFor(selectedRelatedAttribute)}
              step={inputStepFor(selectedRelatedAttribute)}
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
                    {selectedEntity?.visName ?? result.entityAzName}: {formatAttributeValue(selectedDisplayAttribute, result.values[selectedDisplayAttribute?.azName ?? ""])}
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
                        <span>{formatAttributeValue(attribute, value)}</span>
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
  if (window.location.pathname === "/modelInstanceApi") {
    return <ModelInstanceApiPage />;
  }
  if (window.location.pathname === "/visualizeWizard") {
    return <VisualizationWizardPage />;
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

  function openModelInstanceApi(modelAzName: string, instanceRootId: string) {
    setOpenRootMenuKey(null);
    window.open(modelInstanceApiUrl(modelAzName, instanceRootId), "_blank", "noopener,noreferrer");
  }

  function openVisualizeWizard(modelAzName: string, instanceRootId: string) {
    setOpenRootMenuKey(null);
    window.open(visualizeWizardUrl(modelAzName, instanceRootId), "_blank", "noopener,noreferrer");
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
                                          openModelInstanceApi(model.modelAzName, root.instanceRootId);
                                        }}
                                      >
                                        API docs...
                                      </button>
                                      <button
                                        type="button"
                                        role="menuitem"
                                        onClick={(event) => {
                                          event.preventDefault();
                                          openVisualizeWizard(model.modelAzName, root.instanceRootId);
                                        }}
                                      >
                                        Visualize...
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
