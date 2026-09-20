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
type TreeLabelAggregateFunction = "min" | "max" | "avg" | "median" | "variance" | "sum";

type TraversalOption = {
  association: AssociationDescription;
  direction: RelationshipDirection;
  relatedEntity: EntityDescription;
};

type ParentAssociationOption = {
  association: AssociationDescription;
  parentEntity: EntityDescription;
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

type TreeLabelAggregateOption = {
  aggregateFunction: TreeLabelAggregateFunction;
  entityAzName: string;
  attributeAzName: string;
  label: string;
  template: string;
};

type TreeLabelDescendantContext = {
  entityAzName: string;
  values: Record<string, unknown>;
};

type BuiltTidyTreeNode = {
  node: TidyTreeNode;
  descendantContexts: TreeLabelDescendantContext[];
};

type VisualizationDataState = {
  status: ModelInstanceLoadState;
  message: string;
  tree: TidyTreeNode | null;
  hexbinMap: HexbinMapData | null;
  loadedAt?: string;
};

type RootMatchState = {
  status: ModelInstanceLoadState;
  message: string;
  count?: number;
  instance?: EntityInstanceResponse;
};

type LocationPoint = {
  latitude: number;
  longitude: number;
};

type HexbinMapBinding = {
  rootEntityAzName: string;
  rootInstanceId: string;
  areaAttributeAzName: string;
  overlayTraversalValue: string;
  overlayAreaAttributeAzName: string;
  overlayLabelTemplate: string;
  overlayStyleMode: HexbinMapStyleMode;
  manualOverlayStyles: Record<string, HexbinMapManualStyleAssignment>;
  pointContextTraversalValue: string;
  pointTraversalValue: string;
  pointLocationAttributeAzName: string;
  pointStyleAttributeAzName: string;
  pointLegendLabelTemplate: string;
  showUnlinkedPointDiagnostics: boolean;
};

type HexbinMapStyleMode = "automaticPatternColor" | "automaticBorderColor" | "manualBorderColor" | "manualPatternColor";

type HexbinMapPattern = "solid" | "diagonal" | "reverse-diagonal" | "crosshatch" | "dots" | "horizontal" | "vertical";

type HexbinMapManualStyleAssignment = {
  color: string;
  pattern: HexbinMapPattern | "";
};

type HexbinAreaAttributeOption = {
  attribute: AttributeDescription;
  boundary: LocationPoint[] | null;
  disabledReason?: string;
};

type HexbinMapRootOption = {
  entity: EntityDescription;
  instance: EntityInstanceResponse;
  label: string;
  attributes: HexbinAreaAttributeOption[];
  disabledReason?: string;
};

type HexbinMapRootOptionsState = {
  status: ModelInstanceLoadState;
  message: string;
  options: HexbinMapRootOption[];
};

type HexbinMapOverlayPreviewState = {
  status: ModelInstanceLoadState;
  message: string;
  linkedCount?: number;
  renderableCount?: number;
  subregions?: HexbinMapResolvedSubregion[];
};

type HexbinMapStyle = {
  color: string;
  fillColor: string;
  pattern: HexbinMapPattern;
  fillMode: "pattern" | "none";
};

type HexbinMapResolvedSubregion = {
  id: string;
  label: string;
  boundary: LocationPoint[];
  instance: EntityInstanceResponse;
};

type HexbinMapSubregion = {
  id: string;
  label: string;
  boundary: LocationPoint[];
  instance: EntityInstanceResponse;
  style: HexbinMapStyle;
};

type HexbinMapPointContainer = {
  id: string;
  label: string;
  boundary: LocationPoint[];
  instance: EntityInstanceResponse;
  entity: EntityDescription;
};

type HexbinMapSegmentOccurrence = {
  subregionIndex: number;
  start: LocationPoint;
  end: LocationPoint;
  color: string;
};

type HexbinMapPointShape = "circle" | "square" | "triangle" | "cross";

type HexbinMapPointStyle = {
  color: string;
  shape: HexbinMapPointShape;
};

type HexbinMapPoint = {
  id: string;
  label: string;
  location: LocationPoint;
  styleKey: string;
  styleLabel: string;
  style: HexbinMapPointStyle;
  conflict: boolean;
};

type HexbinMapPointLegendEntry = {
  key: string;
  label: string;
  style: HexbinMapPointStyle;
};

type HexbinMapData = {
  title: string;
  detail: string;
  boundary: LocationPoint[];
  subregions: HexbinMapSubregion[];
  points: HexbinMapPoint[];
  pointLegend: HexbinMapPointLegendEntry[];
  warnings: string[];
  overlayNotice?: string;
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
const HEXBIN_MAP_CHART_ID = "hexbin-map";
const TREE_LABEL_AGGREGATE_FUNCTIONS: TreeLabelAggregateFunction[] = ["min", "max", "avg", "median", "variance", "sum"];
const NO_LOCATION_AREA_DATA_REASON = "No LOCATION_AREA data";
const HEXBIN_MAP_STYLE_PATTERNS: HexbinMapPattern[] = ["solid", "diagonal", "reverse-diagonal", "crosshatch", "dots", "horizontal", "vertical"];
const HEXBIN_MAP_PATTERN_LABELS: Record<HexbinMapPattern, string> = {
  solid: "Solid",
  diagonal: "Diagonal stripe",
  "reverse-diagonal": "Reverse diagonal stripe",
  crosshatch: "Crosshatch",
  dots: "Dots",
  horizontal: "Horizontal stripe",
  vertical: "Vertical stripe",
};
const HEXBIN_MAP_STYLE_COLORS = ["#2563eb", "#16a34a", "#dc2626", "#9333ea", "#d97706", "#0f766e", "#475569", "#eab308"];
const HEXBIN_MAP_STYLE_COLOR_LABELS: Record<string, string> = {
  "#2563eb": "Blue",
  "#16a34a": "Green",
  "#dc2626": "Red",
  "#9333ea": "Purple",
  "#d97706": "Orange",
  "#0f766e": "Teal",
  "#475569": "Slate",
  "#eab308": "Yellow",
};
const HEXBIN_MAP_STYLE_COMBINATIONS = HEXBIN_MAP_STYLE_PATTERNS.length * HEXBIN_MAP_STYLE_COLORS.length;
const HEXBIN_MAP_POINT_SHAPES: HexbinMapPointShape[] = ["circle", "square", "triangle", "cross"];
const HEXBIN_MAP_CONFLICT_POINT_STYLE: HexbinMapPointStyle = {
  color: "#111827",
  shape: "cross",
};

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
  {
    id: HEXBIN_MAP_CHART_ID,
    name: "Hexbin-map",
    summary: "Plain SVG LOCATION_AREA map with optional subregion overlays.",
    evaluateEligibility: evaluateHexbinMapEligibility,
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

function evaluateHexbinMapEligibility(apiDescription: ApiDescriptionResponse): ChartEligibility {
  const hasLocationAreaEntity = apiDescription.entities.some((entity) => locationAreaAttributes(entity).length > 0);
  if (!hasLocationAreaEntity) {
    return {
      selectable: false,
      reason: "Hexbin-map needs at least one entity with a LOCATION_AREA attribute.",
    };
  }
  return { selectable: true };
}

function locationAreaAttributes(entity: EntityDescription): AttributeDescription[] {
  return entity.attributes.filter((attribute) => attribute.dataType === "LOCATION_AREA");
}

function emptyHexbinMapBinding(): HexbinMapBinding {
  return {
    rootEntityAzName: "",
    rootInstanceId: "",
    areaAttributeAzName: "",
    overlayTraversalValue: "",
    overlayAreaAttributeAzName: "",
    overlayLabelTemplate: "{id}",
    overlayStyleMode: "automaticPatternColor",
    manualOverlayStyles: {},
    pointContextTraversalValue: "",
    pointTraversalValue: "",
    pointLocationAttributeAzName: "",
    pointStyleAttributeAzName: "",
    pointLegendLabelTemplate: "{id}",
    showUnlinkedPointDiagnostics: false,
  };
}

function withoutHexbinMapPointBinding(binding: HexbinMapBinding): HexbinMapBinding {
  return {
    ...binding,
    pointContextTraversalValue: "",
    pointTraversalValue: "",
    pointLocationAttributeAzName: "",
    pointStyleAttributeAzName: "",
    pointLegendLabelTemplate: "{id}",
    showUnlinkedPointDiagnostics: false,
  };
}

function locationAttributes(entity: EntityDescription): AttributeDescription[] {
  return entity.attributes.filter((attribute) => attribute.dataType === "LOCATION");
}

function parseLocationAreaBoundary(value: unknown): LocationPoint[] | null {
  if (typeof value !== "object" || value === null || !("boundary" in value)) {
    return null;
  }
  const boundary = (value as { boundary?: unknown }).boundary;
  if (!Array.isArray(boundary) || boundary.length < 3) {
    return null;
  }
  const points = boundary.map((candidate): LocationPoint | null => {
    if (typeof candidate !== "object" || candidate === null) {
      return null;
    }
    const latitude = (candidate as { latitude?: unknown }).latitude;
    const longitude = (candidate as { longitude?: unknown }).longitude;
    if (typeof latitude !== "number" || typeof longitude !== "number" || !Number.isFinite(latitude) || !Number.isFinite(longitude)) {
      return null;
    }
    return { latitude, longitude };
  });
  if (points.some((point) => point === null)) {
    return null;
  }
  return points as LocationPoint[];
}

function parseLocationPoint(value: unknown): LocationPoint | null {
  if (typeof value !== "object" || value === null) {
    return null;
  }
  const latitude = (value as { latitude?: unknown }).latitude;
  const longitude = (value as { longitude?: unknown }).longitude;
  if (typeof latitude !== "number" || typeof longitude !== "number" || !Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return null;
  }
  return { latitude, longitude };
}

function instanceOptionLabel(entity: EntityDescription, instance: EntityInstanceResponse): string {
  const labelAttribute = entity.attributes.find((attribute) => attribute.dataType !== "LOCATION_AREA") ?? entity.attributes[0] ?? null;
  const labelValue = labelAttribute === null ? "" : formatAttributeValue(labelAttribute, instance.values[labelAttribute.azName]).trim();
  return labelValue ? `${entity.visName}: ${labelValue}` : `${entity.visName}: ${instance.id}`;
}

async function loadHexbinMapRootOptions(
  apiBaseUrl: string,
  modelAzName: string,
  instanceRootId: string,
  apiDescription: ApiDescriptionResponse,
): Promise<HexbinMapRootOption[]> {
  const locationAreaEntities = apiDescription.entities.filter((entity) => locationAreaAttributes(entity).length > 0);
  const groups = await Promise.all(locationAreaEntities.map(async (entity) => {
    const instances = await queryEntityInstances(apiBaseUrl, modelAzName, instanceRootId, entity.azName, {});
    return { entity, instances };
  }));

  return groups.flatMap(({ entity, instances }) => instances.map((instance) => {
    const attributes = locationAreaAttributes(entity).map((attribute) => {
      const boundary = parseLocationAreaBoundary(instance.values[attribute.azName]);
      return {
        attribute,
        boundary,
        disabledReason: boundary === null ? NO_LOCATION_AREA_DATA_REASON : undefined,
      };
    });
    const hasUsableAttribute = attributes.some((attribute) => attribute.boundary !== null);
    return {
      entity,
      instance,
      label: instanceOptionLabel(entity, instance),
      attributes,
      disabledReason: hasUsableAttribute ? undefined : NO_LOCATION_AREA_DATA_REASON,
    };
  }));
}

function rootOptionValue(option: HexbinMapRootOption): string {
  return `${option.entity.azName}::${option.instance.id}`;
}

function findHexbinRootOption(options: HexbinMapRootOption[], binding: HexbinMapBinding): HexbinMapRootOption | null {
  return options.find((option) => (
    sameAzName(option.entity.azName, binding.rootEntityAzName)
    && option.instance.id === binding.rootInstanceId
  )) ?? null;
}

function firstValidHexbinMapBinding(options: HexbinMapRootOption[]): HexbinMapBinding {
  const rootOption = options.find((option) => option.disabledReason === undefined) ?? null;
  const attributeOption = rootOption?.attributes.find((attribute) => attribute.boundary !== null) ?? null;
  if (rootOption === null || attributeOption === null) {
    return emptyHexbinMapBinding();
  }
  return {
    rootEntityAzName: rootOption.entity.azName,
    rootInstanceId: rootOption.instance.id,
    areaAttributeAzName: attributeOption.attribute.azName,
    overlayTraversalValue: "",
    overlayAreaAttributeAzName: "",
    overlayLabelTemplate: "{id}",
    overlayStyleMode: "automaticPatternColor",
    manualOverlayStyles: {},
    pointContextTraversalValue: "",
    pointTraversalValue: "",
    pointLocationAttributeAzName: "",
    pointStyleAttributeAzName: "",
    pointLegendLabelTemplate: "{id}",
    showUnlinkedPointDiagnostics: false,
  };
}

function hexbinMapOverlayTraversalOptions(
  apiDescription: ApiDescriptionResponse | null,
  rootOption: HexbinMapRootOption | null,
): TraversalOption[] {
  if (rootOption === null) {
    return [];
  }
  return traversalOptionsFor(rootOption.entity, apiDescription)
    .filter(isHexbinMapOverlayTraversalOption)
    .filter((option) => locationAreaAttributes(option.relatedEntity).length > 0);
}

function isHexbinMapAreaDescendantTraversalOption(option: TraversalOption): boolean {
  return !(option.association.kind === "OWNERSHIP" && option.direction === "incoming");
}

function isHexbinMapOverlayTraversalOption(option: TraversalOption): boolean {
  return isHexbinMapAreaDescendantTraversalOption(option);
}

function supportsDesktopDoubleClick(): boolean {
  return typeof window.matchMedia === "function"
    && window.matchMedia("(hover: hover) and (pointer: fine)").matches;
}

function selectedHexbinMapOverlayTraversal(
  apiDescription: ApiDescriptionResponse | null,
  rootOption: HexbinMapRootOption | null,
  binding: HexbinMapBinding,
): TraversalOption | null {
  return hexbinMapOverlayTraversalOptions(apiDescription, rootOption)
    .find((option) => traversalOptionValue(option) === binding.overlayTraversalValue) ?? null;
}

function hexbinMapPointContextEntity(
  rootOption: HexbinMapRootOption | null,
  overlayTraversal: TraversalOption | null,
): EntityDescription | null {
  return overlayTraversal?.relatedEntity ?? rootOption?.entity ?? null;
}

function hexbinMapPointContextTraversalOptions(
  apiDescription: ApiDescriptionResponse | null,
  rootOption: HexbinMapRootOption | null,
  overlayTraversal: TraversalOption | null,
): TraversalOption[] {
  return traversalOptionsFor(hexbinMapPointContextEntity(rootOption, overlayTraversal), apiDescription)
    .filter(isHexbinMapAreaDescendantTraversalOption);
}

function selectedHexbinMapPointContextTraversal(
  apiDescription: ApiDescriptionResponse | null,
  rootOption: HexbinMapRootOption | null,
  overlayTraversal: TraversalOption | null,
  binding: HexbinMapBinding,
): TraversalOption | null {
  return hexbinMapPointContextTraversalOptions(apiDescription, rootOption, overlayTraversal)
    .find((option) => traversalOptionValue(option) === binding.pointContextTraversalValue) ?? null;
}

function hexbinMapPointTraversalOptions(
  apiDescription: ApiDescriptionResponse | null,
  contextTraversal: TraversalOption | null,
): TraversalOption[] {
  if (contextTraversal === null) {
    return [];
  }
  return traversalOptionsFor(contextTraversal.relatedEntity, apiDescription)
    .filter(isHexbinMapAreaDescendantTraversalOption)
    .filter((option) => locationAttributes(option.relatedEntity).length > 0);
}

function selectedHexbinMapPointTraversal(
  apiDescription: ApiDescriptionResponse | null,
  contextTraversal: TraversalOption | null,
  binding: HexbinMapBinding,
): TraversalOption | null {
  return hexbinMapPointTraversalOptions(apiDescription, contextTraversal)
    .find((option) => traversalOptionValue(option) === binding.pointTraversalValue) ?? null;
}

function hexbinMapPatternColorStyleForIndex(index: number): HexbinMapStyle {
  const pattern = HEXBIN_MAP_STYLE_PATTERNS[index % HEXBIN_MAP_STYLE_PATTERNS.length];
  const color = HEXBIN_MAP_STYLE_COLORS[Math.floor(index / HEXBIN_MAP_STYLE_PATTERNS.length) % HEXBIN_MAP_STYLE_COLORS.length];
  return {
    color,
    fillColor: "#ffffff",
    pattern,
    fillMode: "pattern",
  };
}

function hexbinMapBorderColorStyleForIndex(index: number): HexbinMapStyle {
  const color = HEXBIN_MAP_STYLE_COLORS[index % HEXBIN_MAP_STYLE_COLORS.length];
  return {
    color,
    fillColor: "#ffffff",
    pattern: "solid",
    fillMode: "none",
  };
}

function hexbinMapPointStyleForIndex(index: number): HexbinMapPointStyle {
  return {
    color: HEXBIN_MAP_STYLE_COLORS[index % HEXBIN_MAP_STYLE_COLORS.length],
    shape: HEXBIN_MAP_POINT_SHAPES[Math.floor(index / HEXBIN_MAP_STYLE_COLORS.length) % HEXBIN_MAP_POINT_SHAPES.length],
  };
}

function hexbinMapPointKey(point: LocationPoint): string {
  return `${point.latitude},${point.longitude}`;
}

function hexbinMapSamePoint(first: LocationPoint, second: LocationPoint): boolean {
  return first.latitude === second.latitude && first.longitude === second.longitude;
}

function hexbinMapSegmentKey(start: LocationPoint, end: LocationPoint): string {
  const startKey = hexbinMapPointKey(start);
  const endKey = hexbinMapPointKey(end);
  return startKey < endKey ? `${startKey}|${endKey}` : `${endKey}|${startKey}`;
}

function hexbinMapOpenBoundary(boundary: LocationPoint[]): LocationPoint[] {
  if (boundary.length > 1 && hexbinMapSamePoint(boundary[0], boundary[boundary.length - 1])) {
    return boundary.slice(0, -1);
  }
  return boundary;
}

function sharedHexbinMapBorderOccurrences(subregions: HexbinMapSubregion[]): HexbinMapSegmentOccurrence[] {
  const segments = new Map<string, HexbinMapSegmentOccurrence[]>();
  subregions.forEach((subregion, subregionIndex) => {
    const boundary = hexbinMapOpenBoundary(subregion.boundary);
    boundary.forEach((start, pointIndex) => {
      const end = boundary[(pointIndex + 1) % boundary.length];
      if (hexbinMapSamePoint(start, end)) {
        return;
      }
      const key = hexbinMapSegmentKey(start, end);
      const occurrences = segments.get(key) ?? [];
      occurrences.push({
        subregionIndex,
        start,
        end,
        color: subregion.style.color,
      });
      segments.set(key, occurrences);
    });
  });

  return [...segments.values()].flatMap((occurrences) => {
    const uniqueOccurrences = [...occurrences]
      .sort((first, second) => first.subregionIndex - second.subregionIndex)
      .filter((occurrence, index, sorted) => (
        sorted.findIndex((candidate) => candidate.subregionIndex === occurrence.subregionIndex) === index
      ));
    if (uniqueOccurrences.length < 2) {
      return [];
    }
    return uniqueOccurrences;
  });
}

function locationPointInsideBoundary(point: LocationPoint, boundary: LocationPoint[]): boolean {
  const openBoundary = hexbinMapOpenBoundary(boundary);
  if (openBoundary.length < 3) {
    return false;
  }
  let inside = false;
  for (let index = 0, previousIndex = openBoundary.length - 1; index < openBoundary.length; previousIndex = index++) {
    const current = openBoundary[index];
    const previous = openBoundary[previousIndex];
    const crossesLatitude = current.latitude > point.latitude !== previous.latitude > point.latitude;
    if (!crossesLatitude) {
      continue;
    }
    const longitudeAtLatitude = ((previous.longitude - current.longitude) * (point.latitude - current.latitude)) / (previous.latitude - current.latitude) + current.longitude;
    if (point.longitude < longitudeAtLatitude) {
      inside = !inside;
    }
  }
  return inside;
}

function hexbinMapPointBindingEnabled(binding: HexbinMapBinding): boolean {
  return Boolean(
    binding.pointContextTraversalValue
    && binding.pointTraversalValue
    && binding.pointLocationAttributeAzName
    && binding.pointStyleAttributeAzName,
  );
}

function hexbinMapBindingValidationMessage(
  rootOptionsState: HexbinMapRootOptionsState,
  apiDescription: ApiDescriptionResponse | null,
  binding: HexbinMapBinding,
  overlayPreviewState?: HexbinMapOverlayPreviewState,
): string | null {
  if (rootOptionsState.status === "loading") {
    return "Loading Hexbin-map root items.";
  }
  if (rootOptionsState.status === "error") {
    return rootOptionsState.message;
  }
  if (rootOptionsState.options.length === 0) {
    return "No Hexbin-map root items are available.";
  }
  const rootOption = findHexbinRootOption(rootOptionsState.options, binding);
  if (rootOption === null) {
    return "Select one root item.";
  }
  if (rootOption.disabledReason !== undefined) {
    return rootOption.disabledReason;
  }
  const attributeOption = rootOption.attributes.find((candidate) => candidate.attribute.azName === binding.areaAttributeAzName) ?? null;
  if (attributeOption === null) {
    return "Select a LOCATION_AREA attribute.";
  }
  if (attributeOption.boundary === null) {
    return attributeOption.disabledReason ?? NO_LOCATION_AREA_DATA_REASON;
  }
  if (binding.overlayTraversalValue) {
    const traversal = selectedHexbinMapOverlayTraversal(apiDescription, rootOption, binding);
    if (traversal === null) {
      return "Select a valid subregion association.";
    }
    const overlayAttribute = traversal.relatedEntity.attributes.find((candidate) => candidate.azName === binding.overlayAreaAttributeAzName) ?? null;
    if (overlayAttribute === null || overlayAttribute.dataType !== "LOCATION_AREA") {
      return "Select a subregion LOCATION_AREA attribute.";
    }
    const labelTemplateError = validateLabelTemplate(traversal.relatedEntity, binding.overlayLabelTemplate);
    if (labelTemplateError !== null) {
      return `Subregion legend: ${labelTemplateError}`;
    }
    if (binding.overlayStyleMode === "manualBorderColor" || binding.overlayStyleMode === "manualPatternColor") {
      if (overlayPreviewState === undefined || overlayPreviewState.status === "loading") {
        return "Loading subregion manual style rows.";
      }
      if (overlayPreviewState.status === "error") {
        return overlayPreviewState.message;
      }
      const previewSubregions = overlayPreviewState.subregions ?? [];
      if (overlayPreviewState.status !== "ok") {
        return "Load subregion overlay preview before assigning manual styles.";
      }
      for (const subregion of previewSubregions) {
        const style = binding.manualOverlayStyles[subregion.id];
        if (style === undefined || !HEXBIN_MAP_STYLE_COLORS.includes(style.color)) {
          return `Select a manual color for ${subregion.label}.`;
        }
        if (binding.overlayStyleMode === "manualPatternColor" && !HEXBIN_MAP_STYLE_PATTERNS.includes(style.pattern as HexbinMapPattern)) {
          return `Select a manual fill pattern for ${subregion.label}.`;
        }
      }
    }
  }
  if (binding.pointContextTraversalValue || binding.pointTraversalValue || binding.pointLocationAttributeAzName || binding.pointStyleAttributeAzName) {
    const traversal = selectedHexbinMapOverlayTraversal(apiDescription, rootOption, binding);
    const contextTraversal = selectedHexbinMapPointContextTraversal(apiDescription, rootOption, traversal, binding);
    if (contextTraversal === null) {
      return "Select a valid point style context association.";
    }
    const pointTraversal = selectedHexbinMapPointTraversal(apiDescription, contextTraversal, binding);
    if (pointTraversal === null) {
      return "Select a valid point association.";
    }
    const locationAttribute = pointTraversal.relatedEntity.attributes.find((candidate) => candidate.azName === binding.pointLocationAttributeAzName) ?? null;
    if (locationAttribute === null || locationAttribute.dataType !== "LOCATION") {
      return "Select a point LOCATION attribute.";
    }
    const styleAttribute = contextTraversal.relatedEntity.attributes.find((candidate) => candidate.azName === binding.pointStyleAttributeAzName) ?? null;
    if (styleAttribute === null) {
      return "Select a point style attribute.";
    }
    const pointLegendTemplateError = validateLabelTemplate(contextTraversal.relatedEntity, binding.pointLegendLabelTemplate);
    if (pointLegendTemplateError !== null) {
      return `Point legend: ${pointLegendTemplateError}`;
    }
  }
  return null;
}

function styledHexbinMapSubregions(
  resolvedSubregions: HexbinMapResolvedSubregion[],
  binding: HexbinMapBinding,
): HexbinMapSubregion[] {
  return resolvedSubregions.map((subregion, index) => {
    let style: HexbinMapStyle;
    if (binding.overlayStyleMode === "automaticBorderColor") {
      style = hexbinMapBorderColorStyleForIndex(index);
    } else if (binding.overlayStyleMode === "manualBorderColor") {
      const manualStyle = binding.manualOverlayStyles[subregion.id];
      if (manualStyle === undefined || !HEXBIN_MAP_STYLE_COLORS.includes(manualStyle.color)) {
        throw new Error(`Select a manual color for ${subregion.label}.`);
      }
      style = {
        color: manualStyle.color,
        fillColor: "#ffffff",
        pattern: "solid",
        fillMode: "none",
      };
    } else if (binding.overlayStyleMode === "manualPatternColor") {
      const manualStyle = binding.manualOverlayStyles[subregion.id];
      if (manualStyle === undefined || !HEXBIN_MAP_STYLE_COLORS.includes(manualStyle.color)) {
        throw new Error(`Select a manual color for ${subregion.label}.`);
      }
      if (!HEXBIN_MAP_STYLE_PATTERNS.includes(manualStyle.pattern as HexbinMapPattern)) {
        throw new Error(`Select a manual fill pattern for ${subregion.label}.`);
      }
      style = {
        color: manualStyle.color,
        fillColor: "#ffffff",
        pattern: manualStyle.pattern as HexbinMapPattern,
        fillMode: "pattern",
      };
    } else {
      style = hexbinMapPatternColorStyleForIndex(index);
    }
    return {
      ...subregion,
      style,
    };
  });
}

async function resolveHexbinMapSubregions(
  apiBaseUrl: string,
  modelAzName: string,
  instanceRootId: string,
  rootInstanceId: string,
  traversal: TraversalOption,
  areaAttributeAzName: string,
  labelTemplate: string,
): Promise<{
  linkedCount: number;
  skippedCount: number;
  subregions: HexbinMapResolvedSubregion[];
}> {
  const links = await fetchAssociationLinks(apiBaseUrl, modelAzName, instanceRootId, traversal.association.azName);
  const linkedIds = [...new Set(links
    .map((link) => relatedInstanceIdForLink(rootInstanceId, link, traversal.direction))
    .filter((relatedId): relatedId is string => relatedId !== null))]
    .sort((left, right) => left.localeCompare(right));

  const instances = await Promise.all(linkedIds.map((relatedId) => (
    fetchEntityInstance(apiBaseUrl, modelAzName, instanceRootId, traversal.relatedEntity.azName, relatedId)
  )));
  const subregions: HexbinMapResolvedSubregion[] = [];
  for (const instance of instances) {
    const boundary = parseLocationAreaBoundary(instance.values[areaAttributeAzName]);
    if (boundary === null) {
      continue;
    }
    subregions.push({
      id: instance.id,
      label: renderLabelTemplate(traversal.relatedEntity, instance, labelTemplate),
      boundary,
      instance,
    });
  }

  return {
    linkedCount: linkedIds.length,
    skippedCount: linkedIds.length - subregions.length,
    subregions,
  };
}

async function fetchEntityInstancesByIds(
  apiBaseUrl: string,
  modelAzName: string,
  instanceRootId: string,
  entity: EntityDescription,
  instanceIds: Iterable<string>,
): Promise<Map<string, EntityInstanceResponse>> {
  const uniqueIds = [...new Set([...instanceIds])].sort((left, right) => left.localeCompare(right));
  const instances = await Promise.all(uniqueIds.map((instanceId) => (
    fetchEntityInstance(apiBaseUrl, modelAzName, instanceRootId, entity.azName, instanceId)
  )));
  return new Map(instances.map((instance) => [instance.id, instance]));
}

async function resolveHexbinMapPoints(
  apiBaseUrl: string,
  modelAzName: string,
  instanceRootId: string,
  apiDescription: ApiDescriptionResponse,
  containers: HexbinMapPointContainer[],
  rootOption: HexbinMapRootOption,
  overlayTraversal: TraversalOption | null,
  binding: HexbinMapBinding,
): Promise<{
  points: HexbinMapPoint[];
  legend: HexbinMapPointLegendEntry[];
  warnings: string[];
}> {
  if (containers.length === 0) {
    return { points: [], legend: [], warnings: [] };
  }
  const contextTraversal = selectedHexbinMapPointContextTraversal(apiDescription, rootOption, overlayTraversal, binding);
  const pointTraversal = selectedHexbinMapPointTraversal(apiDescription, contextTraversal, binding);
  if (contextTraversal === null || pointTraversal === null) {
    return { points: [], legend: [], warnings: [] };
  }
  const styleAttribute = contextTraversal.relatedEntity.attributes.find((candidate) => candidate.azName === binding.pointStyleAttributeAzName) ?? null;
  const locationAttribute = pointTraversal.relatedEntity.attributes.find((candidate) => candidate.azName === binding.pointLocationAttributeAzName) ?? null;
  if (styleAttribute === null || locationAttribute === null || locationAttribute.dataType !== "LOCATION") {
    return { points: [], legend: [], warnings: [] };
  }

  const contextLinks = await fetchAssociationLinks(apiBaseUrl, modelAzName, instanceRootId, contextTraversal.association.azName);
  const pointLinks = await fetchAssociationLinks(apiBaseUrl, modelAzName, instanceRootId, pointTraversal.association.azName);
  const containerById = new Map(containers.map((container) => [container.id, container]));
  const contextIdsByContainerId = new Map<string, Set<string>>();
  for (const container of containers) {
    for (const link of contextLinks) {
      const contextId = relatedInstanceIdForLink(container.id, link, contextTraversal.direction);
      if (contextId !== null) {
        const ids = contextIdsByContainerId.get(container.id) ?? new Set<string>();
        ids.add(contextId);
        contextIdsByContainerId.set(container.id, ids);
      }
    }
  }
  const contextInstancesById = await fetchEntityInstancesByIds(
    apiBaseUrl,
    modelAzName,
    instanceRootId,
    contextTraversal.relatedEntity,
    [...contextIdsByContainerId.values()].flatMap((ids) => [...ids]),
  );
  const pointIdsByContextId = new Map<string, Set<string>>();
  for (const contextId of contextInstancesById.keys()) {
    for (const link of pointLinks) {
      const pointId = relatedInstanceIdForLink(contextId, link, pointTraversal.direction);
      if (pointId !== null) {
        const ids = pointIdsByContextId.get(contextId) ?? new Set<string>();
        ids.add(pointId);
        pointIdsByContextId.set(contextId, ids);
      }
    }
  }
  const pointInstancesById = await fetchEntityInstancesByIds(
    apiBaseUrl,
    modelAzName,
    instanceRootId,
    pointTraversal.relatedEntity,
    [...pointIdsByContextId.values()].flatMap((ids) => [...ids]),
  );

  type PointPath = {
    container: HexbinMapPointContainer;
    context: EntityInstanceResponse;
    point: EntityInstanceResponse;
    location: LocationPoint;
    styleKey: string;
    styleLabel: string;
  };

  const pathByKey = new Map<string, PointPath>();
  const warnings: string[] = [];
  for (const [containerId, contextIds] of contextIdsByContainerId) {
    const container = containerById.get(containerId);
    if (container === undefined) {
      continue;
    }
    for (const contextId of contextIds) {
      const context = contextInstancesById.get(contextId);
      if (context === undefined) {
        continue;
      }
      const pointIds = pointIdsByContextId.get(contextId) ?? new Set<string>();
      for (const pointId of pointIds) {
        const point = pointInstancesById.get(pointId);
        if (point === undefined) {
          continue;
        }
        const location = parseLocationPoint(point.values[locationAttribute.azName]);
        if (location === null) {
          warnings.push(`${entityInstanceLabel(pointTraversal.relatedEntity, point)} has no usable ${locationAttribute.visName} LOCATION value.`);
          continue;
        }
        const styleValue = formatAttributeValue(styleAttribute, context.values[styleAttribute.azName]) || "n/a";
        const styleLabel = renderLabelTemplate(contextTraversal.relatedEntity, context, binding.pointLegendLabelTemplate);
        const pathKey = `${container.id}::${context.id}::${point.id}::${styleValue}`;
        if (!pathByKey.has(pathKey)) {
          pathByKey.set(pathKey, {
            container,
            context,
            point,
            location,
            styleKey: styleValue,
            styleLabel,
          });
        }
      }
    }
  }

  const pathsByPointId = new Map<string, PointPath[]>();
  for (const path of pathByKey.values()) {
    const paths = pathsByPointId.get(path.point.id) ?? [];
    paths.push(path);
    pathsByPointId.set(path.point.id, paths);
  }

  const categoryLabelsByKey = new Map<string, string>();
  for (const path of pathByKey.values()) {
    categoryLabelsByKey.set(path.styleKey, path.styleLabel);
  }
  const legend = [...categoryLabelsByKey.entries()]
    .sort((left, right) => left[1].localeCompare(right[1]))
    .map(([key, label], index) => ({
      key,
      label,
      style: hexbinMapPointStyleForIndex(index),
    }));
  const styleByKey = new Map(legend.map((entry) => [entry.key, entry.style]));

  const points: HexbinMapPoint[] = [];
  for (const [pointId, paths] of [...pathsByPointId.entries()].sort((left, right) => left[0].localeCompare(right[0]))) {
    const containerIds = new Set(paths.map((path) => path.container.id));
    const styleKeys = new Set(paths.map((path) => path.styleKey));
    const conflict = containerIds.size > 1 || styleKeys.size > 1;
    const firstPath = paths[0];
    if (conflict) {
      if (styleKeys.size > 1) {
        warnings.push(`${entityInstanceLabel(pointTraversal.relatedEntity, firstPath.point)} is reached through multiple ${contextTraversal.relatedEntity.visName} style values.`);
      }
      if (containerIds.size > 1) {
        warnings.push(`${entityInstanceLabel(pointTraversal.relatedEntity, firstPath.point)} is reached through multiple areas.`);
      }
    }
    for (const path of conflict ? [firstPath] : paths) {
      if (!locationPointInsideBoundary(path.location, path.container.boundary)) {
        warnings.push(`${entityInstanceLabel(pointTraversal.relatedEntity, path.point)} is outside associated area ${path.container.label}.`);
      }
      points.push({
        id: path.point.id,
        label: entityInstanceLabel(pointTraversal.relatedEntity, path.point),
        location: path.location,
        styleKey: conflict ? "__conflict" : path.styleKey,
        styleLabel: conflict ? "Conflicting path" : path.styleLabel,
        style: conflict ? HEXBIN_MAP_CONFLICT_POINT_STYLE : styleByKey.get(path.styleKey) ?? hexbinMapPointStyleForIndex(0),
        conflict,
      });
    }
  }

  if (binding.showUnlinkedPointDiagnostics) {
    const linkedPointIds = new Set(pointInstancesById.keys());
    const allPointInstances = await queryEntityInstances(apiBaseUrl, modelAzName, instanceRootId, pointTraversal.relatedEntity.azName, {});
    for (const point of allPointInstances) {
      if (linkedPointIds.has(point.id)) {
        continue;
      }
      const location = parseLocationPoint(point.values[locationAttribute.azName]);
      if (location === null) {
        continue;
      }
      const containingContainer = containers.find((container) => locationPointInsideBoundary(location, container.boundary)) ?? null;
      if (containingContainer !== null) {
        warnings.push(`${entityInstanceLabel(pointTraversal.relatedEntity, point)} is geometrically inside ${containingContainer.label} but is not linked through the selected point path.`);
      }
    }
  }

  return {
    points,
    legend,
    warnings: [...new Set(warnings)],
  };
}

async function buildHexbinMapData(
  apiBaseUrl: string,
  modelAzName: string,
  instanceRootId: string,
  apiDescription: ApiDescriptionResponse,
  rootOptions: HexbinMapRootOption[],
  binding: HexbinMapBinding,
): Promise<HexbinMapData> {
  const rootOption = findHexbinRootOption(rootOptions, binding);
  if (rootOption === null) {
    throw new Error("Select one root item.");
  }
  const attribute = rootOption.entity.attributes.find((candidate) => candidate.azName === binding.areaAttributeAzName) ?? null;
  if (attribute === null || attribute.dataType !== "LOCATION_AREA") {
    throw new Error("Select a LOCATION_AREA attribute.");
  }
  const instance = await fetchEntityInstance(apiBaseUrl, modelAzName, instanceRootId, rootOption.entity.azName, rootOption.instance.id);
  const boundary = parseLocationAreaBoundary(instance.values[attribute.azName]);
  if (boundary === null) {
    throw new Error(NO_LOCATION_AREA_DATA_REASON);
  }
  let subregions: HexbinMapSubregion[] = [];
  let overlayNotice: string | undefined;
  let points: HexbinMapPoint[] = [];
  let pointLegend: HexbinMapPointLegendEntry[] = [];
  let warnings: string[] = [];
  const traversal = selectedHexbinMapOverlayTraversal(apiDescription, rootOption, binding);
  if (binding.overlayTraversalValue && traversal !== null) {
    const resolved = await resolveHexbinMapSubregions(
      apiBaseUrl,
      modelAzName,
      instanceRootId,
      instance.id,
      traversal,
      binding.overlayAreaAttributeAzName,
      binding.overlayLabelTemplate,
    );
    subregions = styledHexbinMapSubregions(resolved.subregions, binding);
    const notices = [];
    if (resolved.skippedCount > 0) {
      notices.push(`${resolved.skippedCount} linked subregion${resolved.skippedCount === 1 ? "" : "s"} skipped because LOCATION_AREA data is missing or invalid`);
    }
    if (binding.overlayStyleMode === "automaticPatternColor" && subregions.length > HEXBIN_MAP_STYLE_COMBINATIONS) {
      notices.push("some subregion styles are reused");
    }
    if (binding.overlayStyleMode === "automaticBorderColor" && subregions.length > HEXBIN_MAP_STYLE_COLORS.length) {
      notices.push("some subregion border colors are reused");
    }
    overlayNotice = notices.length === 0 ? undefined : notices.join("; ");
  }
  if (hexbinMapPointBindingEnabled(binding)) {
    const pointContainers: HexbinMapPointContainer[] = traversal === null
      ? [{
        id: instance.id,
        label: instanceOptionLabel(rootOption.entity, instance),
        boundary,
        instance,
        entity: rootOption.entity,
      }]
      : subregions.map((subregion) => ({
        id: subregion.id,
        label: subregion.label,
        boundary: subregion.boundary,
        instance: subregion.instance,
        entity: traversal.relatedEntity,
      }));
    const resolvedPoints = await resolveHexbinMapPoints(
      apiBaseUrl,
      modelAzName,
      instanceRootId,
      apiDescription,
      pointContainers,
      rootOption,
      traversal,
      binding,
    );
    points = resolvedPoints.points;
    pointLegend = resolvedPoints.legend;
    warnings = resolvedPoints.warnings;
  }
  return {
    title: instanceOptionLabel(rootOption.entity, instance),
    detail: subregions.length === 0
      ? `${attribute.visName} (${attribute.azName})${points.length === 0 ? "" : ` with ${points.length} point${points.length === 1 ? "" : "s"}`}`
      : `${attribute.visName} (${attribute.azName}) with ${subregions.length} subregion overlay${subregions.length === 1 ? "" : "s"}${points.length === 0 ? "" : ` and ${points.length} point${points.length === 1 ? "" : "s"}`}`,
    boundary,
    subregions,
    points,
    pointLegend,
    warnings,
    overlayNotice,
  };
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

function parseTreeLabelAggregatePlaceholder(placeholder: string): {
  aggregateFunction: TreeLabelAggregateFunction;
  entityAzName: string;
  attributeAzName: string;
} | null {
  const match = /^(min|max|avg|median|variance|sum):([A-Za-z0-9_]+)\.([A-Za-z0-9_]+)$/.exec(placeholder.trim());
  if (match === null) {
    return null;
  }
  return {
    aggregateFunction: match[1] as TreeLabelAggregateFunction,
    entityAzName: match[2],
    attributeAzName: match[3],
  };
}

function treeLabelAggregateOptionsForLevel(
  apiDescription: ApiDescriptionResponse | null,
  binding: TidyTreeBinding,
  levelIndex: number,
): TreeLabelAggregateOption[] {
  if (apiDescription === null) {
    return [];
  }
  return binding.levels
    .slice(levelIndex + 1)
    .flatMap((level) => {
      const entity = findEntity(apiDescription.entities, level.entityAzName);
      if (entity === null) {
        return [];
      }
      return entity.attributes
        .filter((attribute) => attribute.dataType === "NUMERIC")
        .flatMap((attribute) => TREE_LABEL_AGGREGATE_FUNCTIONS.map((aggregateFunction) => ({
          aggregateFunction,
          entityAzName: entity.azName,
          attributeAzName: attribute.azName,
          label: `${aggregateFunction} ${entity.visName}.${attribute.visName}`,
          template: `{${aggregateFunction}:${entity.azName}.${attribute.azName}}`,
        })));
    });
}

function treeLabelAggregateOptionsForEntity(entity: EntityDescription): TreeLabelAggregateOption[] {
  return entity.attributes
    .filter((attribute) => attribute.dataType === "NUMERIC")
    .flatMap((attribute) => TREE_LABEL_AGGREGATE_FUNCTIONS.map((aggregateFunction) => ({
      aggregateFunction,
      entityAzName: entity.azName,
      attributeAzName: attribute.azName,
      label: `${aggregateFunction} ${entity.visName}.${attribute.visName}`,
      template: `{${aggregateFunction}:${entity.azName}.${attribute.azName}}`,
    })));
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

function validateTreeLabelTemplate(
  entity: EntityDescription,
  template: string,
  aggregateOptions: TreeLabelAggregateOption[],
): string | null {
  if (!template.trim()) {
    return "Label template is required.";
  }
  const attributeNames = new Set(entity.attributes.map((attribute) => attribute.azName.toLocaleLowerCase()));
  const aggregateTemplateNames = new Set(aggregateOptions.map((option) => option.template.slice(1, -1).toLocaleLowerCase()));
  for (const placeholder of templatePlaceholders(template)) {
    if (placeholder === "id") {
      continue;
    }
    const aggregatePlaceholder = parseTreeLabelAggregatePlaceholder(placeholder);
    if (aggregatePlaceholder !== null) {
      if (!aggregateTemplateNames.has(placeholder.toLocaleLowerCase())) {
        return `${placeholder} is not a reachable numeric aggregate.`;
      }
      continue;
    }
    if (placeholder.includes(":")) {
      return `${placeholder} is not a valid aggregate expression.`;
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

function renderTreeLabelTemplate(
  entity: EntityDescription,
  instance: EntityInstanceResponse,
  template: string,
  descendantContexts: TreeLabelDescendantContext[],
): string {
  const rendered = template.replace(/\{([^{}]+)\}/g, (_match, rawPlaceholder: string) => {
    const placeholder = rawPlaceholder.trim();
    const aggregatePlaceholder = parseTreeLabelAggregatePlaceholder(placeholder);
    if (aggregatePlaceholder !== null) {
      const values = descendantContexts
        .filter((context) => sameAzName(context.entityAzName, aggregatePlaceholder.entityAzName))
        .map((context) => context.values[aggregatePlaceholder.attributeAzName])
        .filter((value): value is number => typeof value === "number" && Number.isFinite(value));
      return formatTreeLabelAggregateValue(aggregatePlaceholder.aggregateFunction, values);
    }
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

function formatTreeLabelAggregateValue(aggregateFunction: TreeLabelAggregateFunction, values: number[]): string {
  if (values.length === 0) {
    return "n/a";
  }
  const sortedValues = [...values].sort((left, right) => left - right);
  let aggregateValue: number;
  if (aggregateFunction === "min") {
    aggregateValue = sortedValues[0];
  } else if (aggregateFunction === "max") {
    aggregateValue = sortedValues[sortedValues.length - 1];
  } else if (aggregateFunction === "median") {
    const middleIndex = Math.floor(sortedValues.length / 2);
    aggregateValue = sortedValues.length % 2 === 0
      ? (sortedValues[middleIndex - 1] + sortedValues[middleIndex]) / 2
      : sortedValues[middleIndex];
  } else {
    const sum = sortedValues.reduce((total, value) => total + value, 0);
    if (aggregateFunction === "sum") {
      aggregateValue = sum;
    } else if (aggregateFunction === "variance") {
      const average = sum / sortedValues.length;
      aggregateValue = sortedValues.reduce((total, value) => total + (value - average) ** 2, 0) / sortedValues.length;
    } else {
      aggregateValue = sum / sortedValues.length;
    }
  }
  return Number.isInteger(aggregateValue)
    ? String(aggregateValue)
    : aggregateValue.toLocaleString(undefined, { maximumFractionDigits: 6 });
}

function treeLabelDescendantContextsForBuiltChildren(children: BuiltTidyTreeNode[]): TreeLabelDescendantContext[] {
  return children.flatMap((child) => [
    ...child.descendantContexts,
  ]);
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
  const labelTemplateError = validateTreeLabelTemplate(rootEntity, binding.rootSelection.labelTemplate, treeLabelAggregateOptionsForLevel(apiDescription, binding, 0));
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
    const templateError = validateTreeLabelTemplate(entity, level.labelTemplate, treeLabelAggregateOptionsForLevel(apiDescription, binding, index));
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

  function buildLevelNode(levelIndex: number, instance: EntityInstanceResponse, visitedPath = new Set<string>(), labelTemplateOverride?: string): BuiltTidyTreeNode {
    const level = binding.levels[levelIndex];
    const entity = entityByAzName.get(level.entityAzName.toLocaleLowerCase());
    if (entity === undefined) {
      throw new Error(`Entity ${level.entityAzName} is unavailable.`);
    }
    const nextVisitedPath = new Set(visitedPath);
    nextVisitedPath.add(instance.id);

    const nextLevel = binding.levels[levelIndex + 1] ?? null;
    let builtChildren: BuiltTidyTreeNode[] = [];
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
      builtChildren = nextEntity === undefined
        ? []
        : childInstances
            .map((childInstance) => buildLevelNode(levelIndex + 1, childInstance, nextVisitedPath))
            .sort((left, right) => left.node.label.localeCompare(right.node.label));
    }
    const descendantContexts = treeLabelDescendantContextsForBuiltChildren(builtChildren);
    const label = renderTreeLabelTemplate(entity, instance, labelTemplateOverride ?? level.labelTemplate, descendantContexts);

    return {
      node: {
        id: instance.id,
        label,
        detail: entity.visName,
        children: builtChildren.map((child) => child.node),
      },
      descendantContexts: [
        {
          entityAzName: entity.azName,
          values: instance.values,
        },
        ...descendantContexts,
      ],
    };
  }

  const firstLevel = binding.levels[0];
  const firstEntity = entityByAzName.get(firstLevel.entityAzName.toLocaleLowerCase());
  const firstInstances = instancesByLevel.get(0) ?? [];
  if (binding.rootSelection.mode === "entity") {
    if (firstEntity === undefined || firstInstances[0] === undefined) {
      throw new Error("Resolved root instance is unavailable.");
    }
    return buildLevelNode(0, firstInstances[0], new Set<string>(), binding.rootSelection.labelTemplate).node;
  }
  const builtChildren = firstEntity === undefined
    ? []
    : firstInstances
        .map((instance) => buildLevelNode(0, instance))
        .sort((left, right) => left.node.label.localeCompare(right.node.label));
  return {
    id: "root",
    label: binding.rootLabel.trim(),
    detail: apiDescription.modelVisName,
    children: builtChildren.map((child) => child.node),
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

function parentAssociationOptionsFor(entity: EntityDescription | null, apiDescription: ApiDescriptionResponse | null): ParentAssociationOption[] {
  if (entity === null || apiDescription === null) {
    return [];
  }

  return (apiDescription.associations ?? []).flatMap((association) => {
    if (!sameAzName(association.targetEntityAzName, entity.azName)) {
      return [];
    }
    const parentEntity = findEntity(apiDescription.entities, association.sourceEntityAzName);
    return parentEntity === null ? [] : [{ association, parentEntity }];
  });
}

function parentAssociationLabel(option: ParentAssociationOption): string {
  const roleLabel = option.association.sourceRoleName?.trim();
  const parentLabel = roleLabel ? `${option.parentEntity.visName} (${roleLabel})` : option.parentEntity.visName;
  return `${option.association.visName} (${parentLabel} -> ${option.association.targetEntityAzName}, ${option.association.kind})`;
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
  const [selectedParentAssociationAzName, setSelectedParentAssociationAzName] = useState("");
  const [parentInstances, setParentInstances] = useState<EntityInstanceResponse[]>([]);
  const [selectedParentInstanceId, setSelectedParentInstanceId] = useState("");
  const [createdParentAssociationLink, setCreatedParentAssociationLink] = useState<AssociationLinkResponse | null>(null);
  const [parentLinkError, setParentLinkError] = useState("");
  const [status, setStatus] = useState<ModelInstanceLoadState>("loading");
  const [statusMessage, setStatusMessage] = useState("Loading editor...");
  const [isSaving, setIsSaving] = useState(false);
  const [isSavingAssociation, setIsSavingAssociation] = useState(false);
  const [isLoadingParentInstances, setIsLoadingParentInstances] = useState(false);

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
  const parentAssociationOptions = useMemo(
    () => parentAssociationOptionsFor(selectedEntity, apiDescription),
    [selectedEntity, apiDescription],
  );
  const selectedParentAssociationOption = parentAssociationOptions.find((option) => option.association.azName === selectedParentAssociationAzName) ?? null;
  const showParentLinkSection = parentAssociationOptions.length > 0
    && (willCreate || parentLinkError.length > 0 || createdParentAssociationLink !== null);

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
    setParentInstances([]);
    setSelectedParentInstanceId("");
    if (!willCreate || parentAssociationOptions.length === 0) {
      setSelectedParentAssociationAzName("");
      return;
    }
    if (parentAssociationOptions.length === 1) {
      setSelectedParentAssociationAzName(parentAssociationOptions[0].association.azName);
      return;
    }
    if (!parentAssociationOptions.some((option) => option.association.azName === selectedParentAssociationAzName)) {
      setSelectedParentAssociationAzName("");
    }
  }, [willCreate, parentAssociationOptions]);

  useEffect(() => {
    let cancelled = false;

    async function loadParentInstances() {
      if (
        !willCreate
        || !apiBaseUrl
        || !selectedModelAzName
        || !selectedRootId
        || selectedParentAssociationOption === null
      ) {
        setParentInstances([]);
        setSelectedParentInstanceId("");
        setIsLoadingParentInstances(false);
        return;
      }

      setIsLoadingParentInstances(true);
      setParentLinkError("");
      try {
        const nextParentInstances = await queryEntityInstances(
          apiBaseUrl,
          selectedModelAzName,
          selectedRootId,
          selectedParentAssociationOption.parentEntity.azName,
          {},
        );
        if (cancelled) {
          return;
        }
        setParentInstances(nextParentInstances);
        setSelectedParentInstanceId((current) => (
          nextParentInstances.some((instance) => instance.id === current) ? current : nextParentInstances[0]?.id ?? ""
        ));
      } catch (error) {
        if (!cancelled) {
          setParentInstances([]);
          setSelectedParentInstanceId("");
          setParentLinkError(error instanceof Error ? error.message : "Parent instances load failed");
        }
      } finally {
        if (!cancelled) {
          setIsLoadingParentInstances(false);
        }
      }
    }

    void loadParentInstances();

    return () => {
      cancelled = true;
    };
  }, [apiBaseUrl, selectedModelAzName, selectedRootId, selectedParentAssociationOption, willCreate]);

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
    setSelectedParentAssociationAzName("");
    setParentInstances([]);
    setSelectedParentInstanceId("");
    setCreatedParentAssociationLink(null);
    setParentLinkError("");
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
    setSelectedParentAssociationAzName("");
    setParentInstances([]);
    setSelectedParentInstanceId("");
    setCreatedParentAssociationLink(null);
    setParentLinkError("");
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
    setSelectedParentAssociationAzName("");
    setParentInstances([]);
    setSelectedParentInstanceId("");
    setCreatedParentAssociationLink(null);
    setParentLinkError("");
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

  function selectParentAssociation(nextAssociationAzName: string) {
    setSelectedParentAssociationAzName(nextAssociationAzName);
    setParentInstances([]);
    setSelectedParentInstanceId("");
    setCreatedParentAssociationLink(null);
    setParentLinkError("");
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
    if (willCreate && selectedParentAssociationOption !== null && !selectedParentInstanceId) {
      setStatus("error");
      setStatusMessage("Select a parent instance or choose no parent link");
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
      if (willCreate && selectedParentAssociationOption !== null && selectedParentInstanceId) {
        try {
          setStatusMessage("Creating parent link...");
          const linked = await createAssociationLink(
            apiBaseUrl,
            selectedModelAzName,
            selectedRootId,
            selectedParentAssociationOption.association.azName,
            selectedParentInstanceId,
            saved.id,
          );
          setCreatedParentAssociationLink(linked);
          setParentLinkError("");
          setStatus("ok");
          setStatusMessage(`Created ${selectedEntity.visName} and linked parent`);
          return;
        } catch (linkError) {
          const message = linkError instanceof Error ? linkError.message : "Parent link save failed";
          setCreatedParentAssociationLink(null);
          setParentLinkError(message);
          setStatus("error");
          setStatusMessage(`Created ${selectedEntity.visName}, but parent link failed: ${message}`);
          return;
        }
      }
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
                    onChange={(event) => {
                      setCreateCopy(event.target.checked);
                      setCreatedParentAssociationLink(null);
                      setParentLinkError("");
                    }}
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

            {showParentLinkSection && (
              <section className="editor-parent-link" aria-labelledby="editor-parent-link-heading">
                <div>
                  <h2 id="editor-parent-link-heading">Parent link</h2>
                </div>
                <div className="editor-context-grid">
                  <div className="query-field">
                    <label htmlFor="editor-parent-association">Association</label>
                    <select
                      id="editor-parent-association"
                      value={selectedParentAssociationAzName}
                      onChange={(event) => selectParentAssociation(event.target.value)}
                      disabled={!willCreate || status === "loading" || isSaving}
                    >
                      <option value="">No parent link</option>
                      {parentAssociationOptions.map((option) => (
                        <option key={option.association.azName} value={option.association.azName}>
                          {parentAssociationLabel(option)}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="query-field">
                    <label htmlFor="editor-parent-instance">
                      {selectedParentAssociationOption === null
                        ? "Parent instance"
                        : associationEndpointLabel(selectedParentAssociationOption.parentEntity, selectedParentAssociationOption.association.sourceRoleName)}
                    </label>
                    <select
                      id="editor-parent-instance"
                      value={selectedParentInstanceId}
                      onChange={(event) => setSelectedParentInstanceId(event.target.value)}
                      disabled={
                        !willCreate
                        || status === "loading"
                        || isSaving
                        || isLoadingParentInstances
                        || selectedParentAssociationOption === null
                        || parentInstances.length === 0
                      }
                    >
                      {selectedParentAssociationOption === null ? (
                        <option value="">No association selected</option>
                      ) : isLoadingParentInstances ? (
                        <option value="">Loading parent instances</option>
                      ) : parentInstances.length === 0 ? (
                        <option value="">No parent instances</option>
                      ) : parentInstances.map((instance) => (
                        <option key={instance.id} value={instance.id}>
                          {entityInstanceLabel(selectedParentAssociationOption.parentEntity, instance)}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>
                {parentLinkError && (
                  <div className="editor-link-error">
                    <span>Parent link error</span>
                    <strong>{parentLinkError}</strong>
                  </div>
                )}
                {createdParentAssociationLink !== null && (
                  <div className="editor-link-summary">
                    <span>Created parent link</span>
                    <strong>{createdParentAssociationLink.id}</strong>
                  </div>
                )}
              </section>
            )}

            <div className="editor-actions">
              <span className={`model-status model-status-${status}`}>{statusMessage}</span>
              <button type="submit" disabled={isSaving || status === "loading" || selectedEntity === null || !selectedRootId}>
                {willCreate && selectedParentAssociationOption !== null ? "Create and link" : willCreate ? "Create" : "Save"}
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
  const [hexbinMapBinding, setHexbinMapBinding] = useState<HexbinMapBinding>(emptyHexbinMapBinding());
  const [hexbinMapRootOptionsState, setHexbinMapRootOptionsState] = useState<HexbinMapRootOptionsState>({
    status: "idle",
    message: "Hexbin-map root items not loaded",
    options: [],
  });
  const [hexbinMapOverlayPreviewState, setHexbinMapOverlayPreviewState] = useState<HexbinMapOverlayPreviewState>({
    status: "idle",
    message: "No subregion overlay selected",
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
    hexbinMap: null,
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
    if (!apiBaseUrl || apiDescription === null || !modelAzName || !instanceRootId) {
      setHexbinMapRootOptionsState({
        status: "idle",
        message: "Hexbin-map root items unavailable",
        options: [],
      });
      setHexbinMapBinding(emptyHexbinMapBinding());
      return;
    }
    if (!evaluateHexbinMapEligibility(apiDescription).selectable) {
      setHexbinMapRootOptionsState({
        status: "idle",
        message: "Hexbin-map needs LOCATION_AREA model attributes",
        options: [],
      });
      setHexbinMapBinding(emptyHexbinMapBinding());
      return;
    }

    let cancelled = false;
    setHexbinMapRootOptionsState((current) => ({
      status: "loading",
      message: "Loading Hexbin-map root items...",
      options: current.options,
    }));
    loadHexbinMapRootOptions(apiBaseUrl, modelAzName, instanceRootId, apiDescription)
      .then((options) => {
        if (cancelled) {
          return;
        }
        setHexbinMapRootOptionsState({
          status: "ok",
          message: `${options.length} root item${options.length === 1 ? "" : "s"} loaded`,
          options,
        });
        setHexbinMapBinding((current) => {
          const rootOption = findHexbinRootOption(options, current);
          const attributeOption = rootOption?.attributes.find((candidate) => candidate.attribute.azName === current.areaAttributeAzName) ?? null;
          if (rootOption !== null && rootOption.disabledReason === undefined && attributeOption?.boundary !== null && attributeOption !== null) {
            return current;
          }
          return firstValidHexbinMapBinding(options);
        });
      })
      .catch((error) => {
        if (cancelled) {
          return;
        }
        setHexbinMapRootOptionsState({
          status: "error",
          message: error instanceof Error ? error.message : "Hexbin-map root items failed to load",
          options: [],
        });
        setHexbinMapBinding(emptyHexbinMapBinding());
      });

    return () => {
      cancelled = true;
    };
  }, [apiBaseUrl, apiDescription, instanceRootId, modelAzName]);

  useEffect(() => {
    const rootOption = findHexbinRootOption(hexbinMapRootOptionsState.options, hexbinMapBinding);
    const traversal = selectedHexbinMapOverlayTraversal(apiDescription, rootOption, hexbinMapBinding);
    const overlayAttribute = traversal?.relatedEntity.attributes.find((candidate) => candidate.azName === hexbinMapBinding.overlayAreaAttributeAzName) ?? null;
    if (!apiBaseUrl || apiDescription === null || !modelAzName || !instanceRootId || rootOption === null || traversal === null || overlayAttribute === null) {
      setHexbinMapOverlayPreviewState({
        status: "idle",
        message: hexbinMapBinding.overlayTraversalValue ? "Subregion overlay selection is incomplete" : "No subregion overlay selected",
      });
      return;
    }
    const labelTemplateError = validateLabelTemplate(traversal.relatedEntity, hexbinMapBinding.overlayLabelTemplate);
    if (labelTemplateError !== null) {
      setHexbinMapOverlayPreviewState({
        status: "idle",
        message: `Subregion legend: ${labelTemplateError}`,
      });
      return;
    }

    let cancelled = false;
    const timeoutId = window.setTimeout(() => {
      setHexbinMapOverlayPreviewState({
        status: "loading",
        message: "Loading subregion overlay preview...",
      });
      resolveHexbinMapSubregions(
        apiBaseUrl,
        modelAzName,
        instanceRootId,
        rootOption.instance.id,
        traversal,
        overlayAttribute.azName,
        hexbinMapBinding.overlayLabelTemplate,
      )
        .then((resolved) => {
          if (cancelled) {
            return;
          }
          const skipped = resolved.skippedCount === 0
            ? ""
            : `, ${resolved.skippedCount} without usable LOCATION_AREA data`;
          setHexbinMapOverlayPreviewState({
            status: "ok",
            message: `${resolved.subregions.length} of ${resolved.linkedCount} linked subregion${resolved.linkedCount === 1 ? "" : "s"} renderable${skipped}`,
            linkedCount: resolved.linkedCount,
            renderableCount: resolved.subregions.length,
            subregions: resolved.subregions,
          });
        })
        .catch((error) => {
          if (cancelled) {
            return;
          }
          setHexbinMapOverlayPreviewState({
            status: "error",
            message: error instanceof Error ? error.message : "Subregion overlay preview failed",
          });
        });
    }, 250);

    return () => {
      cancelled = true;
      window.clearTimeout(timeoutId);
    };
  }, [
    apiBaseUrl,
    apiDescription,
    hexbinMapBinding.areaAttributeAzName,
    hexbinMapBinding.overlayAreaAttributeAzName,
    hexbinMapBinding.overlayLabelTemplate,
    hexbinMapBinding.overlayTraversalValue,
    hexbinMapBinding.rootEntityAzName,
    hexbinMapBinding.rootInstanceId,
    hexbinMapRootOptionsState.options,
    instanceRootId,
    modelAzName,
  ]);

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
  const isHexbinMapSelected = selectedChartType.id === HEXBIN_MAP_CHART_ID;
  const hexbinMapBindingMessage = hexbinMapBindingValidationMessage(hexbinMapRootOptionsState, apiDescription, hexbinMapBinding, hexbinMapOverlayPreviewState);
  const selectedBindingMessage = isHexbinMapSelected ? hexbinMapBindingMessage : bindingMessage;
  const canContinueToBinding = status === "ok" && selectedChartEligibility.selectable;
  const canRenderVisualization = canContinueToBinding && selectedBindingMessage === null;

  function clearVisualizationData() {
    setVisualizationData({
      status: "idle",
      message: "Visualization data not loaded",
      tree: null,
      hexbinMap: null,
    });
  }

  function selectChartType(chartTypeId: string, selectable: boolean) {
    if (!selectable) {
      return;
    }
    setSelectedChartTypeId(chartTypeId);
    clearVisualizationData();
  }

  function selectChartTypeAndContinue(chartTypeId: string, selectable: boolean) {
    if (status !== "ok" || !selectable || !supportsDesktopDoubleClick()) {
      return;
    }
    selectChartType(chartTypeId, selectable);
    setStep("binding");
  }

  function updateHexbinMapBinding(nextBinding: HexbinMapBinding) {
    setHexbinMapBinding(nextBinding);
    clearVisualizationData();
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
        hexbinMap: null,
      });
      return;
    }
    const validationError = isHexbinMapSelected
      ? hexbinMapBindingValidationMessage(hexbinMapRootOptionsState, apiDescription, hexbinMapBinding, hexbinMapOverlayPreviewState)
      : bindingValidationMessage(apiDescription, binding);
    if (validationError !== null) {
      setVisualizationData({
        status: "error",
        message: validationError,
        tree: null,
        hexbinMap: null,
      });
      return;
    }

    setVisualizationData({
      status: "loading",
      message: "Loading visualization data...",
      tree: visualizationData.tree,
      hexbinMap: visualizationData.hexbinMap,
    });
    try {
      if (isHexbinMapSelected) {
        const hexbinMap = await buildHexbinMapData(
          apiBaseUrl,
          apiDescription.modelAzName,
          instanceRootId,
          apiDescription,
          hexbinMapRootOptionsState.options,
          hexbinMapBinding,
        );
        setVisualizationData({
          status: "ok",
          message: hexbinMap.subregions.length === 0
            ? `${hexbinMap.boundary.length} boundary point${hexbinMap.boundary.length === 1 ? "" : "s"} rendered`
            : `${hexbinMap.subregions.length} subregion overlay${hexbinMap.subregions.length === 1 ? "" : "s"} rendered`,
          tree: null,
          hexbinMap,
          loadedAt: new Date().toLocaleTimeString(),
        });
        return;
      }

      const tree = await buildTidyTreeData(apiBaseUrl, apiDescription.modelAzName, instanceRootId, apiDescription, binding);
      setVisualizationData({
        status: "ok",
        message: `${tree.children.length} top-level node${tree.children.length === 1 ? "" : "s"} rendered`,
        tree,
        hexbinMap: null,
        loadedAt: new Date().toLocaleTimeString(),
      });
    } catch (error) {
      setVisualizationData({
        status: "error",
        message: error instanceof Error ? error.message : "Visualization data load failed",
        tree: null,
        hexbinMap: null,
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
                  onClick={() => selectChartType(chartType.id, eligibility.selectable)}
                  onDoubleClick={() => selectChartTypeAndContinue(chartType.id, eligibility.selectable)}
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

        {step === "binding" && isHexbinMapSelected && (
          <HexbinMapBindingPanel
            apiDescription={apiDescription}
            rootOptionsState={hexbinMapRootOptionsState}
            overlayPreviewState={hexbinMapOverlayPreviewState}
            binding={hexbinMapBinding}
            validationMessage={hexbinMapBindingMessage}
            onBindingChange={updateHexbinMapBinding}
            onVisualize={() => {
              setStep("visualization");
              void loadVisualizationData();
            }}
          />
        )}

        {step === "binding" && !isHexbinMapSelected && (
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
              {isHexbinMapSelected ? (
                visualizationData.hexbinMap === null ? (
                  <div className="tree-empty">{visualizationData.status === "loading" ? "Loading map..." : "No visualization data"}</div>
                ) : (
                  <HexbinMapRenderer data={visualizationData.hexbinMap} />
                )
              ) : visualizationData.tree === null ? (
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

function HexbinMapBindingPanel({
  apiDescription,
  rootOptionsState,
  overlayPreviewState,
  binding,
  validationMessage,
  onBindingChange,
  onVisualize,
}: {
  apiDescription: ApiDescriptionResponse | null;
  rootOptionsState: HexbinMapRootOptionsState;
  overlayPreviewState: HexbinMapOverlayPreviewState;
  binding: HexbinMapBinding;
  validationMessage: string | null;
  onBindingChange: (value: HexbinMapBinding) => void;
  onVisualize: () => void;
}) {
  const selectedRootOption = findHexbinRootOption(rootOptionsState.options, binding);
  const selectedRootValue = selectedRootOption === null ? "" : rootOptionValue(selectedRootOption);
  const overlayTraversalOptions = hexbinMapOverlayTraversalOptions(apiDescription, selectedRootOption);
  const selectedOverlayTraversal = selectedHexbinMapOverlayTraversal(apiDescription, selectedRootOption, binding);
  const overlayAreaAttributes = selectedOverlayTraversal === null ? [] : locationAreaAttributes(selectedOverlayTraversal.relatedEntity);
  const pointContextEntity = hexbinMapPointContextEntity(selectedRootOption, selectedOverlayTraversal);
  const pointContextTraversalOptions = hexbinMapPointContextTraversalOptions(apiDescription, selectedRootOption, selectedOverlayTraversal);
  const selectedPointContextTraversal = selectedHexbinMapPointContextTraversal(apiDescription, selectedRootOption, selectedOverlayTraversal, binding);
  const pointTraversalOptions = hexbinMapPointTraversalOptions(apiDescription, selectedPointContextTraversal);
  const selectedPointTraversal = selectedHexbinMapPointTraversal(apiDescription, selectedPointContextTraversal, binding);
  const pointLocationAttributes = selectedPointTraversal === null ? [] : locationAttributes(selectedPointTraversal.relatedEntity);
  const pointStyleAttributes = selectedPointContextTraversal === null ? [] : selectedPointContextTraversal.relatedEntity.attributes;
  const hasNoSelectableOverlayAssociation = selectedRootOption !== null
    && selectedRootOption.disabledReason === undefined
    && overlayTraversalOptions.length === 0;

  function selectRoot(rootValue: string) {
    const nextRootOption = rootOptionsState.options.find((option) => rootOptionValue(option) === rootValue) ?? null;
    if (nextRootOption === null || nextRootOption.disabledReason !== undefined) {
      onBindingChange(emptyHexbinMapBinding());
      return;
    }
    const firstAttribute = nextRootOption.attributes.find((attribute) => attribute.boundary !== null) ?? null;
    onBindingChange({
      rootEntityAzName: nextRootOption.entity.azName,
      rootInstanceId: nextRootOption.instance.id,
      areaAttributeAzName: firstAttribute?.attribute.azName ?? "",
      overlayTraversalValue: "",
      overlayAreaAttributeAzName: "",
      overlayLabelTemplate: "{id}",
      overlayStyleMode: "automaticPatternColor",
      manualOverlayStyles: {},
      pointContextTraversalValue: "",
      pointTraversalValue: "",
      pointLocationAttributeAzName: "",
      pointStyleAttributeAzName: "",
      pointLegendLabelTemplate: "{id}",
      showUnlinkedPointDiagnostics: false,
    });
  }

  function selectOverlayTraversal(traversalValue: string) {
    const nextTraversal = overlayTraversalOptions.find((option) => traversalOptionValue(option) === traversalValue) ?? null;
    const nextAttribute = nextTraversal === null ? null : locationAreaAttributes(nextTraversal.relatedEntity)[0] ?? null;
    onBindingChange(withoutHexbinMapPointBinding({
      ...binding,
      overlayTraversalValue: traversalValue,
      overlayAreaAttributeAzName: nextAttribute?.azName ?? "",
      overlayLabelTemplate: nextTraversal === null ? "{id}" : defaultLabelTemplate(nextTraversal.relatedEntity),
      overlayStyleMode: "automaticPatternColor",
      manualOverlayStyles: {},
    }));
  }

  function selectPointContextTraversal(traversalValue: string) {
    const nextTraversal = pointContextTraversalOptions.find((option) => traversalOptionValue(option) === traversalValue) ?? null;
    const nextPointTraversal = hexbinMapPointTraversalOptions(apiDescription, nextTraversal)[0] ?? null;
    const nextLocationAttribute = nextPointTraversal === null ? null : locationAttributes(nextPointTraversal.relatedEntity)[0] ?? null;
    const nextStyleAttribute = nextTraversal?.relatedEntity.attributes[0] ?? null;
    onBindingChange({
      ...binding,
      pointContextTraversalValue: traversalValue,
      pointTraversalValue: nextPointTraversal === null ? "" : traversalOptionValue(nextPointTraversal),
      pointLocationAttributeAzName: nextLocationAttribute?.azName ?? "",
      pointStyleAttributeAzName: nextStyleAttribute?.azName ?? "",
      pointLegendLabelTemplate: nextStyleAttribute === null ? "{id}" : `{${nextStyleAttribute.azName}}`,
    });
  }

  function selectPointTraversal(traversalValue: string) {
    const nextTraversal = pointTraversalOptions.find((option) => traversalOptionValue(option) === traversalValue) ?? null;
    const nextLocationAttribute = nextTraversal === null ? null : locationAttributes(nextTraversal.relatedEntity)[0] ?? null;
    onBindingChange({
      ...binding,
      pointTraversalValue: traversalValue,
      pointLocationAttributeAzName: nextLocationAttribute?.azName ?? "",
    });
  }

  function selectOverlayStyleMode(overlayStyleMode: HexbinMapStyleMode) {
    onBindingChange({
      ...binding,
      overlayStyleMode,
      manualOverlayStyles: overlayStyleMode === "manualBorderColor" || overlayStyleMode === "manualPatternColor"
        ? binding.manualOverlayStyles
        : {},
    });
  }

  function updateManualOverlayStyle(subregionId: string, nextStyle: HexbinMapManualStyleAssignment) {
    onBindingChange({
      ...binding,
      manualOverlayStyles: {
        ...binding.manualOverlayStyles,
        [subregionId]: nextStyle,
      },
    });
  }

  const manualStyleSubregions = overlayPreviewState.status === "ok" ? overlayPreviewState.subregions ?? [] : [];
  const showsManualStyles = selectedOverlayTraversal !== null
    && (binding.overlayStyleMode === "manualBorderColor" || binding.overlayStyleMode === "manualPatternColor");

  return (
    <section className="visualize-panel" aria-labelledby="visualize-hexbin-binding">
      <h2 id="visualize-hexbin-binding">Hexbin-map Binding</h2>
      <div className="binding-grid binding-grid-two">
        <label className="query-field">
          <span>Root item</span>
          <select
            value={selectedRootValue}
            onChange={(event) => selectRoot(event.target.value)}
            disabled={rootOptionsState.status === "loading"}
          >
            {selectedRootOption === null && (
              <option value="">
                {rootOptionsState.options.length === 0 ? "No LOCATION_AREA root items" : "Select a root item"}
              </option>
            )}
            {rootOptionsState.options.map((option) => (
              <option key={rootOptionValue(option)} value={rootOptionValue(option)} disabled={option.disabledReason !== undefined}>
                {option.label}{option.disabledReason === undefined ? "" : ` - ${option.disabledReason}`}
              </option>
            ))}
          </select>
        </label>
        <label className="query-field">
          <span>Boundary attribute</span>
          <select
            value={binding.areaAttributeAzName}
            onChange={(event) => onBindingChange({ ...binding, areaAttributeAzName: event.target.value })}
            disabled={selectedRootOption === null || selectedRootOption.disabledReason !== undefined}
          >
            {selectedRootOption === null ? (
              <option value="">Select a root item</option>
            ) : selectedRootOption.attributes.map((option) => (
              <option key={option.attribute.azName} value={option.attribute.azName} disabled={option.disabledReason !== undefined}>
                {option.attribute.visName} ({option.attribute.azName}){option.disabledReason === undefined ? "" : ` - ${option.disabledReason}`}
              </option>
            ))}
          </select>
        </label>
      </div>

      <section className="hexbin-overlay-binding" aria-labelledby="visualize-hexbin-overlay">
        <header>
          <h3 id="visualize-hexbin-overlay">Subregion Overlay</h3>
          <span>Optional layer from linked LOCATION_AREA instances</span>
        </header>
        <div className="binding-grid binding-grid-two">
          <label className="query-field query-field-wide">
            <span>Subregion association</span>
            <select
              value={binding.overlayTraversalValue}
              onChange={(event) => selectOverlayTraversal(event.target.value)}
              disabled={selectedRootOption === null || selectedRootOption.disabledReason !== undefined}
            >
              <option value="">{hasNoSelectableOverlayAssociation ? "No selectable overlay association" : "No overlay"}</option>
              {overlayTraversalOptions.map((option) => (
                <option key={traversalOptionValue(option)} value={traversalOptionValue(option)}>
                  {traversalLabel(option)}
                </option>
              ))}
            </select>
            {hasNoSelectableOverlayAssociation && (
              <span className="binding-field-note">
                The selected root item has no valid association to LOCATION_AREA subregion items.
              </span>
            )}
          </label>
          <label className="query-field">
            <span>Subregion boundary</span>
            <select
              value={binding.overlayAreaAttributeAzName}
              onChange={(event) => onBindingChange({ ...binding, overlayAreaAttributeAzName: event.target.value })}
              disabled={selectedOverlayTraversal === null}
            >
              {selectedOverlayTraversal === null ? (
                <option value="">Select an overlay association</option>
              ) : overlayAreaAttributes.map((attribute) => (
                <option key={attribute.azName} value={attribute.azName}>
                  {attribute.visName} ({attribute.azName})
                </option>
              ))}
            </select>
          </label>
          <label className="query-field">
            <span>Style assignment</span>
            <select
              value={binding.overlayStyleMode}
              onChange={(event) => selectOverlayStyleMode(event.target.value as HexbinMapStyleMode)}
              disabled={selectedOverlayTraversal === null}
            >
              <option value="automaticPatternColor">Automatic pattern and color per subregion</option>
              <option value="automaticBorderColor">Automatic border color per subregion</option>
              <option value="manualBorderColor">Manual border color per subregion</option>
              <option value="manualPatternColor">Manual fill pattern and color per subregion</option>
            </select>
          </label>
          <label className="query-field">
            <span>Legend label</span>
            <input
              value={binding.overlayLabelTemplate}
              placeholder="{id}"
              onChange={(event) => onBindingChange({ ...binding, overlayLabelTemplate: event.target.value })}
              disabled={selectedOverlayTraversal === null}
            />
          </label>
        </div>
        {selectedOverlayTraversal !== null && (
          <div className="binding-template-hints" aria-label="Subregion legend label template hints">
            {[...selectedOverlayTraversal.relatedEntity.attributes.map((attribute) => `{${attribute.azName}}`), "{id}"].map((hint) => (
              <button
                key={hint}
                type="button"
                onClick={() => onBindingChange({ ...binding, overlayLabelTemplate: `${binding.overlayLabelTemplate}${hint}` })}
                title={`Append ${hint}`}
              >
                <code>{hint}</code>
              </button>
            ))}
          </div>
        )}
        <footer className={`binding-root-match binding-root-match-${overlayPreviewState.status}`}>
          {overlayPreviewState.message}
        </footer>
        {showsManualStyles && (
          <div className="hexbin-manual-styles" aria-label="Manual subregion styles">
            <div className="hexbin-manual-styles-header">
              <span>Subregion</span>
              <span>Color</span>
              {binding.overlayStyleMode === "manualPatternColor" && <span>Fill pattern</span>}
            </div>
            {manualStyleSubregions.length === 0 ? (
              <p>No renderable subregions available for manual style assignment.</p>
            ) : manualStyleSubregions.map((subregion) => {
              const manualStyle = binding.manualOverlayStyles[subregion.id] ?? { color: "", pattern: "" };
              return (
                <div className="hexbin-manual-style-row" key={subregion.id}>
                  <span title={subregion.id}>{subregion.label}</span>
                  <label className="query-field">
                    <span className="sr-only">Color for {subregion.label}</span>
                    <select
                      value={manualStyle.color}
                      onChange={(event) => updateManualOverlayStyle(subregion.id, {
                        ...manualStyle,
                        color: event.target.value,
                      })}
                    >
                      <option value="">Select color</option>
                      {HEXBIN_MAP_STYLE_COLORS.map((color) => (
                        <option key={color} value={color}>
                          {HEXBIN_MAP_STYLE_COLOR_LABELS[color] ?? color}
                        </option>
                      ))}
                    </select>
                  </label>
                  {binding.overlayStyleMode === "manualPatternColor" && (
                    <label className="query-field">
                      <span className="sr-only">Fill pattern for {subregion.label}</span>
                      <select
                        value={manualStyle.pattern}
                        onChange={(event) => updateManualOverlayStyle(subregion.id, {
                          ...manualStyle,
                          pattern: event.target.value as HexbinMapPattern | "",
                        })}
                      >
                        <option value="">Select pattern</option>
                        {HEXBIN_MAP_STYLE_PATTERNS.map((pattern) => (
                          <option key={pattern} value={pattern}>
                            {HEXBIN_MAP_PATTERN_LABELS[pattern]}
                          </option>
                        ))}
                      </select>
                    </label>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </section>

      <section className="hexbin-overlay-binding" aria-labelledby="visualize-hexbin-points">
        <header>
          <h3 id="visualize-hexbin-points">Point Overlay</h3>
          <span>Optional markers from linked LOCATION instances</span>
        </header>
        <div className="binding-grid binding-grid-two">
          <label className="query-field query-field-wide">
            <span>Point style context</span>
            <select
              value={binding.pointContextTraversalValue}
              onChange={(event) => selectPointContextTraversal(event.target.value)}
              disabled={pointContextEntity === null}
            >
              <option value="">{pointContextEntity === null ? "Select a root item first" : "No point overlay"}</option>
              {pointContextTraversalOptions.map((option) => (
                <option key={traversalOptionValue(option)} value={traversalOptionValue(option)}>
                  {traversalLabel(option)}
                </option>
              ))}
            </select>
          </label>
          <label className="query-field query-field-wide">
            <span>Point association</span>
            <select
              value={binding.pointTraversalValue}
              onChange={(event) => selectPointTraversal(event.target.value)}
              disabled={selectedPointContextTraversal === null}
            >
              {selectedPointContextTraversal === null ? (
                <option value="">Select a style context</option>
              ) : pointTraversalOptions.length === 0 ? (
                <option value="">No linked LOCATION point entity</option>
              ) : pointTraversalOptions.map((option) => (
                <option key={traversalOptionValue(option)} value={traversalOptionValue(option)}>
                  {traversalLabel(option)}
                </option>
              ))}
            </select>
          </label>
          <label className="query-field">
            <span>Point location</span>
            <select
              value={binding.pointLocationAttributeAzName}
              onChange={(event) => onBindingChange({ ...binding, pointLocationAttributeAzName: event.target.value })}
              disabled={selectedPointTraversal === null}
            >
              {selectedPointTraversal === null ? (
                <option value="">Select a point association</option>
              ) : pointLocationAttributes.map((attribute) => (
                <option key={attribute.azName} value={attribute.azName}>
                  {attribute.visName} ({attribute.azName})
                </option>
              ))}
            </select>
          </label>
          <label className="query-field">
            <span>Point style</span>
            <select
              value={binding.pointStyleAttributeAzName}
              onChange={(event) => onBindingChange({
                ...binding,
                pointStyleAttributeAzName: event.target.value,
                pointLegendLabelTemplate: event.target.value ? `{${event.target.value}}` : "{id}",
              })}
              disabled={selectedPointContextTraversal === null}
            >
              {selectedPointContextTraversal === null ? (
                <option value="">Select a style context</option>
              ) : pointStyleAttributes.map((attribute) => (
                <option key={attribute.azName} value={attribute.azName}>
                  {attribute.visName} ({attribute.azName})
                </option>
              ))}
            </select>
          </label>
          <label className="query-field">
            <span>Point legend label</span>
            <input
              value={binding.pointLegendLabelTemplate}
              placeholder="{id}"
              onChange={(event) => onBindingChange({ ...binding, pointLegendLabelTemplate: event.target.value })}
              disabled={selectedPointContextTraversal === null}
            />
          </label>
          <label className="query-criterion-toggle">
            <input
              type="checkbox"
              checked={binding.showUnlinkedPointDiagnostics}
              onChange={(event) => onBindingChange({ ...binding, showUnlinkedPointDiagnostics: event.target.checked })}
              disabled={selectedPointTraversal === null}
            />
            {selectedOverlayTraversal === null ? "Find unlinked points inside root area" : "Find unlinked points inside subregions"}
          </label>
        </div>
        {selectedPointContextTraversal !== null && (
          <div className="binding-template-hints" aria-label="Point legend label template hints">
            {[...selectedPointContextTraversal.relatedEntity.attributes.map((attribute) => `{${attribute.azName}}`), "{id}"].map((hint) => (
              <button
                key={hint}
                type="button"
                onClick={() => onBindingChange({ ...binding, pointLegendLabelTemplate: `${binding.pointLegendLabelTemplate}${hint}` })}
                title={`Append ${hint}`}
              >
                <code>{hint}</code>
              </button>
            ))}
          </div>
        )}
      </section>

      <footer className={`binding-root-match binding-root-match-${rootOptionsState.status}`}>
        {rootOptionsState.message}
      </footer>

      {validationMessage && <span className="dialog-error">{validationMessage}</span>}

      <div className="visualize-actions">
        <button type="button" onClick={onVisualize} disabled={validationMessage !== null}>
          Visualize
        </button>
      </div>
    </section>
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
  const rootAggregateOptions = treeLabelAggregateOptionsForLevel(apiDescription, binding, 0);
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

  function appendAggregateToParentLabel(index: number, hint: string) {
    if (index <= 0) {
      return;
    }
    const parentIndex = index - 1;
    if (parentIndex === 0 && binding.rootSelection.mode === "entity") {
      setRootLabelTemplate(`${binding.rootSelection.labelTemplate}${hint}`);
      return;
    }
    const parentLevel = binding.levels[parentIndex] ?? null;
    if (parentLevel === null) {
      return;
    }
    onLevelChange(parentIndex, {
      ...parentLevel,
      labelTemplate: `${parentLevel.labelTemplate}${hint}`,
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

          {rootEntity !== null && (
            <div className="binding-template-hints" aria-label="Root label template hints">
              {[
                ...rootEntity.attributes.map((attribute) => ({
                  template: `{${attribute.azName}}`,
                  title: `Append {${attribute.azName}}`,
                })),
                { template: "{id}", title: "Append {id}" },
                ...rootAggregateOptions.map((option) => ({
                  template: option.template,
                  title: `Append ${option.label}`,
                })),
              ].map((hint, hintIndex) => (
                <button
                  key={`${hint.template}-${hintIndex}`}
                  type="button"
                  onClick={() => setRootLabelTemplate(`${binding.rootSelection.labelTemplate}${hint.template}`)}
                  title={hint.title}
                >
                  <code>{hint.template}</code>
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
          const aggregateOptions = treeLabelAggregateOptionsForLevel(apiDescription, binding, index);
          const currentLevelAggregateOptions = entity === null ? [] : treeLabelAggregateOptionsForEntity(entity);
          const parentAggregateTargetLabel = index === 1 && binding.rootSelection.mode === "entity"
            ? "root label"
            : `Level ${index} label`;
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
              {entity !== null && !(index === 0 && binding.rootSelection.mode === "entity") && (
                <div className="binding-template-hints" aria-label={`Level ${index + 1} label template hints`}>
                  {[
                    ...entity.attributes.map((attribute) => ({
                      template: `{${attribute.azName}}`,
                      title: `Insert {${attribute.azName}}`,
                    })),
                    { template: "{id}", title: "Insert {id}" },
                    ...aggregateOptions.map((option) => ({
                      template: option.template,
                      title: `Insert ${option.label}`,
                    })),
                  ].map((hint, hintIndex) => (
                    <button
                      key={`${hint.template}-${hintIndex}`}
                      type="button"
                      onPointerDown={(event: PointerEvent<HTMLButtonElement>) => event.preventDefault()}
                      onClick={() => insertLabelTemplateHint(index, level, hint.template)}
                      title={hint.title}
                    >
                      <code>{hint.template}</code>
                    </button>
                  ))}
                </div>
              )}
              {index > 0 && currentLevelAggregateOptions.length > 0 && (
                <div className="binding-template-hints" aria-label={`Level ${index + 1} aggregate label templates`}>
                  {currentLevelAggregateOptions.map((option) => (
                    <button
                      key={option.template}
                      type="button"
                      onPointerDown={(event: PointerEvent<HTMLButtonElement>) => event.preventDefault()}
                      onClick={() => appendAggregateToParentLabel(index, option.template)}
                      title={`Insert ${option.label} into ${parentAggregateTargetLabel}`}
                    >
                      <code>{parentAggregateTargetLabel}: {option.template}</code>
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

function HexbinMapRenderer({ data }: { data: HexbinMapData }) {
  const svgRef = useRef<SVGSVGElement>(null);

  useEffect(() => {
    const svgElement = svgRef.current;
    if (svgElement === null) {
      return;
    }

    const width = 960;
    const height = 640;
    const padding = 48;
    const allBoundaries = [data.boundary, ...data.subregions.map((subregion) => subregion.boundary)];
    const allPoints = [...allBoundaries.flat(), ...data.points.map((point) => point.location)];
    const longitudes = allPoints.map((point) => point.longitude);
    const latitudes = allPoints.map((point) => point.latitude);
    const longitudeExtent = d3.extent(longitudes);
    const latitudeExtent = d3.extent(latitudes);
    const minLongitude = longitudeExtent[0] ?? 0;
    const maxLongitude = longitudeExtent[1] ?? minLongitude;
    const minLatitude = latitudeExtent[0] ?? 0;
    const maxLatitude = latitudeExtent[1] ?? minLatitude;
    const longitudeSpan = Math.max(maxLongitude - minLongitude, 0.000001);
    const latitudeSpan = Math.max(maxLatitude - minLatitude, 0.000001);
    const scale = Math.min((width - padding * 2) / longitudeSpan, (height - padding * 2) / latitudeSpan);
    const mapWidth = longitudeSpan * scale;
    const mapHeight = latitudeSpan * scale;
    const offsetX = (width - mapWidth) / 2;
    const offsetY = (height - mapHeight) / 2;
    const projectPoint = (point: LocationPoint): [number, number] => [
      offsetX + (point.longitude - minLongitude) * scale,
      offsetY + (maxLatitude - point.latitude) * scale,
    ];
    const projectBoundary = (boundary: LocationPoint[]) => boundary.map(projectPoint);
    const closeBoundary = (boundary: [number, number][]) => boundary.length > 0
      ? [...boundary, boundary[0]]
      : boundary;
    const projectedBoundary = projectBoundary(data.boundary);
    const closedBoundary = closeBoundary(projectedBoundary);
    const sharedBorderStrokeOffset = 1.8;
    const projectedSubregions = data.subregions.map((subregion) => ({
      subregion,
      boundary: projectBoundary(subregion.boundary),
      openBoundary: projectBoundary(hexbinMapOpenBoundary(subregion.boundary)),
    }));
    const polygonSignedArea = (boundary: [number, number][]) => {
      if (boundary.length < 3) {
        return 0;
      }
      return d3.pairs(closeBoundary(boundary))
        .reduce((sum, [start, end]) => sum + start[0] * end[1] - end[0] * start[1], 0) / 2;
    };
    const sharedBorderStrokes = sharedHexbinMapBorderOccurrences(data.subregions).flatMap((occurrence) => {
      const subregion = projectedSubregions[occurrence.subregionIndex];
      if (subregion === undefined) {
        return [];
      }
      const start = projectPoint(occurrence.start);
      const end = projectPoint(occurrence.end);
      const dx = end[0] - start[0];
      const dy = end[1] - start[1];
      const length = Math.hypot(dx, dy);
      if (length === 0) {
        return [];
      }
      const normalX = (-dy / length) * sharedBorderStrokeOffset;
      const normalY = (dx / length) * sharedBorderStrokeOffset;
      const direction = polygonSignedArea(subregion.openBoundary) >= 0 ? 1 : -1;
      return [{
        color: occurrence.color,
        x1: start[0] + normalX * direction,
        y1: start[1] + normalY * direction,
        x2: end[0] + normalX * direction,
        y2: end[1] + normalY * direction,
      }];
    });
    const line = d3.line<[number, number]>()
      .x((point) => point[0])
      .y((point) => point[1]);
    const symbolTypeForShape = (shape: HexbinMapPointShape) => {
      if (shape === "square") {
        return d3.symbolSquare;
      }
      if (shape === "triangle") {
        return d3.symbolTriangle;
      }
      if (shape === "cross") {
        return d3.symbolCross;
      }
      return d3.symbolCircle;
    };
    const symbolPathForStyle = (style: HexbinMapPointStyle, size: number) => d3.symbol()
      .type(symbolTypeForShape(style.shape))
      .size(size)();

    const svg = d3.select(svgElement);
    svg.selectAll("*").remove();
    svg
      .attr("viewBox", `0 0 ${width} ${height}`)
      .attr("width", width)
      .attr("height", height);

    const defs = svg.append("defs");
    data.subregions.forEach((subregion, index) => {
      const pattern = defs.append("pattern")
        .attr("id", `hexbin-pattern-${index}`)
        .attr("patternUnits", "userSpaceOnUse")
        .attr("width", 10)
        .attr("height", 10);
      pattern.append("rect")
        .attr("width", 10)
        .attr("height", 10)
        .attr("fill", subregion.style.fillColor);
      if (subregion.style.pattern === "diagonal" || subregion.style.pattern === "crosshatch") {
        pattern.append("path")
          .attr("d", "M-2,10 L10,-2 M0,12 L12,0")
          .attr("stroke", subregion.style.color)
          .attr("stroke-width", 2);
      }
      if (subregion.style.pattern === "reverse-diagonal" || subregion.style.pattern === "crosshatch") {
        pattern.append("path")
          .attr("d", "M-2,0 L10,12 M0,-2 L12,10")
          .attr("stroke", subregion.style.color)
          .attr("stroke-width", 2);
      }
      if (subregion.style.pattern === "horizontal") {
        pattern.append("path")
          .attr("d", "M0,3 L10,3 M0,8 L10,8")
          .attr("stroke", subregion.style.color)
          .attr("stroke-width", 2);
      }
      if (subregion.style.pattern === "vertical") {
        pattern.append("path")
          .attr("d", "M3,0 L3,10 M8,0 L8,10")
          .attr("stroke", subregion.style.color)
          .attr("stroke-width", 2);
      }
      if (subregion.style.pattern === "dots") {
        pattern.append("circle")
          .attr("cx", 3)
          .attr("cy", 3)
          .attr("r", 1.8)
          .attr("fill", subregion.style.color);
        pattern.append("circle")
          .attr("cx", 8)
          .attr("cy", 8)
          .attr("r", 1.8)
          .attr("fill", subregion.style.color);
      }
      if (subregion.style.pattern === "solid") {
        pattern.append("rect")
          .attr("width", 10)
          .attr("height", 10)
          .attr("fill", subregion.style.color)
          .attr("opacity", 0.22);
      }
    });

    svg.append("rect")
      .attr("class", "hexbin-map-background")
      .attr("x", 0)
      .attr("y", 0)
      .attr("width", width)
      .attr("height", height);

    svg.append("path")
      .attr("class", "hexbin-map-boundary-fill")
      .attr("d", line(closedBoundary));

    svg.append("path")
      .attr("class", "hexbin-map-boundary")
      .attr("d", line(closedBoundary));

    svg.append("g")
      .attr("class", "hexbin-map-subregions")
      .selectAll("path")
      .data(projectedSubregions)
      .join("path")
      .attr("d", (projectedSubregion) => line(closeBoundary(projectedSubregion.boundary)))
      .attr("fill", (projectedSubregion, index) => projectedSubregion.subregion.style.fillMode === "none" ? "transparent" : `url(#hexbin-pattern-${index})`)
      .attr("stroke", (projectedSubregion) => projectedSubregion.subregion.style.color);

    svg.append("g")
      .attr("class", "hexbin-map-shared-borders")
      .selectAll("line")
      .data(sharedBorderStrokes)
      .join("line")
      .attr("x1", (stroke) => stroke.x1)
      .attr("y1", (stroke) => stroke.y1)
      .attr("x2", (stroke) => stroke.x2)
      .attr("y2", (stroke) => stroke.y2)
      .attr("stroke", (stroke) => stroke.color);

    svg.append("g")
      .attr("class", "hexbin-map-points")
      .selectAll("path")
      .data(data.points.map((point) => ({
        ...point,
        projectedLocation: projectPoint(point.location),
      })))
      .join("path")
      .attr("d", (point) => symbolPathForStyle(point.style, point.conflict ? 92 : 72))
      .attr("transform", (point) => `translate(${point.projectedLocation[0]}, ${point.projectedLocation[1]})`)
      .attr("fill", (point) => point.style.color)
      .attr("stroke", "#ffffff")
      .append("title")
      .text((point) => `${point.label} - ${point.styleLabel}`);

    svg.append("text")
      .attr("class", "hexbin-map-title")
      .attr("x", padding)
      .attr("y", 34)
      .text(data.title);

    svg.append("text")
      .attr("class", "hexbin-map-detail")
      .attr("x", padding)
      .attr("y", 56)
      .text(data.detail);

    if (data.overlayNotice) {
      svg.append("text")
        .attr("class", "hexbin-map-notice")
        .attr("x", padding)
        .attr("y", 78)
        .text(data.overlayNotice);
    }

    if (data.subregions.length > 0) {
      const legendWidth = 268;
      const legendTitleY = 25;
      const legendEntryTop = 48;
      const legendEntrySpacing = 30;
      const legendBottomPadding = 18;
      const legendHeight = legendEntryTop + Math.max(0, data.subregions.length - 1) * legendEntrySpacing + 18 + legendBottomPadding;
      const legend = svg.append("g")
        .attr("class", "hexbin-map-legend")
        .attr("transform", `translate(${width - legendWidth - padding}, ${padding})`);
      legend.append("rect")
        .attr("class", "hexbin-map-legend-background")
        .attr("width", legendWidth)
        .attr("height", legendHeight);
      legend.append("text")
        .attr("class", "hexbin-map-legend-title")
        .attr("x", 14)
        .attr("y", legendTitleY)
        .text("Subregions");
      const entries = legend.append("g")
        .attr("transform", `translate(14, ${legendEntryTop})`)
        .selectAll("g")
        .data(data.subregions)
        .join("g")
        .attr("transform", (_subregion, index) => `translate(0, ${index * legendEntrySpacing})`);
      entries.append("rect")
        .attr("width", 18)
        .attr("height", 18)
        .attr("rx", 2)
        .attr("fill", (subregion, index) => subregion.style.fillMode === "none" ? "transparent" : `url(#hexbin-pattern-${index})`)
        .attr("stroke", (subregion) => subregion.style.color);
      entries.append("text")
        .attr("x", 26)
        .attr("y", 14)
        .text((subregion) => subregion.label);
    }

    if (data.pointLegend.length > 0 || data.points.some((point) => point.conflict)) {
      const entriesData = data.points.some((point) => point.conflict)
        ? [...data.pointLegend, { key: "__conflict", label: "Conflicting path", style: HEXBIN_MAP_CONFLICT_POINT_STYLE }]
        : data.pointLegend;
      const legendWidth = 268;
      const legendTitleY = 25;
      const legendEntryTop = 48;
      const legendEntrySpacing = 28;
      const legendBottomPadding = 18;
      const legendHeight = legendEntryTop + Math.max(0, entriesData.length - 1) * legendEntrySpacing + 18 + legendBottomPadding;
      const legend = svg.append("g")
        .attr("class", "hexbin-map-legend hexbin-map-point-legend")
        .attr("transform", `translate(${padding}, ${height - legendHeight - padding})`);
      legend.append("rect")
        .attr("class", "hexbin-map-legend-background")
        .attr("width", legendWidth)
        .attr("height", legendHeight);
      legend.append("text")
        .attr("class", "hexbin-map-legend-title")
        .attr("x", 14)
        .attr("y", legendTitleY)
        .text("Points");
      const entries = legend.append("g")
        .attr("transform", `translate(23, ${legendEntryTop + 8})`)
        .selectAll("g")
        .data(entriesData)
        .join("g")
        .attr("transform", (_entry, index) => `translate(0, ${index * legendEntrySpacing})`);
      entries.append("path")
        .attr("d", (entry) => symbolPathForStyle(entry.style, 72))
        .attr("fill", (entry) => entry.style.color)
        .attr("stroke", "#ffffff");
      entries.append("text")
        .attr("x", 18)
        .attr("y", 5)
        .text((entry) => entry.label);
    }
  }, [data]);

  return (
    <>
      <svg ref={svgRef} className="hexbin-map-svg" role="img" aria-label="Hexbin-map boundary" />
      {data.warnings.length > 0 && (
        <div className="hexbin-map-warnings" aria-label="Hexbin-map data warnings">
          <h3>Data warnings</h3>
          <ul>
            {data.warnings.map((warning) => (
              <li key={warning}>{warning}</li>
            ))}
          </ul>
        </div>
      )}
    </>
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
