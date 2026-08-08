import vizGlobalUrl from "@plantuml/core/viz-global.js?url";

type PlantUmlModule = typeof import("@plantuml/core");

type ParsedClass = {
  id: string;
  label: string;
  attributes: string[];
};

type ParsedAssociation = {
  sourceId: string;
  targetId: string;
  operator: string;
  sourceEndLabel: string;
  targetEndLabel: string;
  label: string;
};

type ParsedDiagram = {
  title: string;
  classes: ParsedClass[];
  associations: ParsedAssociation[];
};

type LayoutClass = ParsedClass & {
  x: number;
  y: number;
  width: number;
  height: number;
};

const SVG_NS = "http://www.w3.org/2000/svg";

let plantUmlModulePromise: Promise<PlantUmlModule> | null = null;
let plantUmlRenderQueue: Promise<void> = Promise.resolve();

function loadClassicScript(src: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const existingScript = document.querySelector<HTMLScriptElement>(`script[data-vedenemo-src="${src}"]`);
    if (existingScript !== null) {
      if (existingScript.dataset.loaded === "true") {
        resolve();
        return;
      }
      existingScript.addEventListener("load", () => resolve(), { once: true });
      existingScript.addEventListener("error", () => reject(new Error(`failed to load ${src}`)), { once: true });
      return;
    }

    const script = document.createElement("script");
    script.src = src;
    script.async = true;
    script.dataset.vedenemoSrc = src;
    script.addEventListener(
      "load",
      () => {
        script.dataset.loaded = "true";
        resolve();
      },
      { once: true },
    );
    script.addEventListener("error", () => reject(new Error(`failed to load ${src}`)), { once: true });
    document.head.appendChild(script);
  });
}

function loadPlantUmlModule(): Promise<PlantUmlModule> {
  plantUmlModulePromise ??= loadClassicScript(vizGlobalUrl).then(() => import("@plantuml/core"));
  return plantUmlModulePromise;
}

export class PlantUmlDiagramRendererAdapter {
  async renderSvg(source: string, targetId: string): Promise<void> {
    const previousRender = plantUmlRenderQueue;
    let releaseRender: () => void = () => {};
    plantUmlRenderQueue = new Promise((resolve) => {
      releaseRender = resolve;
    });

    await previousRender.catch(() => undefined);

    try {
      const target = document.getElementById(targetId);
      if (target === null) {
        throw new Error("diagram target is unavailable");
      }

      target.replaceChildren();

      const svg = await renderPlantUmlSvgString(source).catch(() => renderFallbackClassDiagramSvgString(source));

      const parsedSvg = new DOMParser().parseFromString(svg, "image/svg+xml").documentElement;
      if (parsedSvg.tagName.toLocaleLowerCase() !== "svg") {
        throw new Error("diagram renderer returned invalid SVG");
      }

      target.replaceChildren(parsedSvg);
    } finally {
      releaseRender();
    }
  }
}

async function renderPlantUmlSvgString(source: string): Promise<string> {
  const { renderToString } = await loadPlantUmlModule();

  return new Promise<string>((resolve, reject) => {
    const timeoutId = window.setTimeout(() => {
      reject(new Error("PlantUML renderer did not complete"));
    }, 10000);

    renderToString(
      source.split(/\r?\n/),
      (svg) => {
        window.clearTimeout(timeoutId);
        resolve(svg);
      },
      (message) => {
        window.clearTimeout(timeoutId);
        reject(new Error(message));
      },
    );
  });
}

function renderFallbackClassDiagramSvgString(source: string): string {
  const diagram = parseVedenemoClassDiagram(source);
  if (diagram.classes.length === 0) {
    throw new Error("PlantUML renderer failed and no fallback class diagram was available");
  }

  const layout = layoutClasses(diagram.classes);
  const width = Math.max(720, ...layout.map((item) => item.x + item.width + 48));
  const height = Math.max(360, ...layout.map((item) => item.y + item.height + 48));
  const svg = document.createElementNS(SVG_NS, "svg");
  svg.setAttribute("xmlns", SVG_NS);
  svg.setAttribute("viewBox", `0 0 ${width} ${height}`);
  svg.setAttribute("role", "img");

  if (diagram.title) {
    const title = document.createElementNS(SVG_NS, "title");
    title.textContent = diagram.title;
    svg.appendChild(title);
  }

  const edgeLayer = document.createElementNS(SVG_NS, "g");
  edgeLayer.setAttribute("fill", "none");
  edgeLayer.setAttribute("stroke", "#607083");
  edgeLayer.setAttribute("stroke-width", "1.5");
  svg.appendChild(edgeLayer);

  const labelsLayer = document.createElementNS(SVG_NS, "g");
  labelsLayer.setAttribute("fill", "#344255");
  labelsLayer.setAttribute("font-family", "Inter, Segoe UI, sans-serif");
  labelsLayer.setAttribute("font-size", "12");
  svg.appendChild(labelsLayer);

  const byId = new Map(layout.map((item) => [item.id, item]));
  for (const association of diagram.associations) {
    const sourceClass = byId.get(association.sourceId);
    const targetClass = byId.get(association.targetId);
    if (sourceClass === undefined || targetClass === undefined) {
      continue;
    }
    appendAssociation(edgeLayer, labelsLayer, sourceClass, targetClass, association);
  }

  const classLayer = document.createElementNS(SVG_NS, "g");
  classLayer.setAttribute("font-family", "Inter, Segoe UI, sans-serif");
  classLayer.setAttribute("font-size", "13");
  svg.appendChild(classLayer);

  for (const item of layout) {
    appendClassBox(classLayer, item);
  }

  return new XMLSerializer().serializeToString(svg);
}

function parseVedenemoClassDiagram(source: string): ParsedDiagram {
  const lines = source.split(/\r?\n/);
  const classes: ParsedClass[] = [];
  const associations: ParsedAssociation[] = [];
  let title = "";

  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index].trim();
    const titleMatch = /^title\s+(.+)$/.exec(line);
    if (titleMatch !== null) {
      title = unescapePlantUmlText(titleMatch[1]);
      continue;
    }

    const classMatch = /^class\s+([A-Za-z0-9_]+)\s+as\s+"((?:\\"|[^"])*)"\s*\{$/.exec(line);
    if (classMatch !== null) {
      const attributes: string[] = [];
      index += 1;
      while (index < lines.length && lines[index].trim() !== "}") {
        const attribute = lines[index].trim();
        if (attribute) {
          attributes.push(unescapePlantUmlText(attribute));
        }
        index += 1;
      }
      classes.push({
        id: classMatch[1],
        label: unescapePlantUmlText(classMatch[2]),
        attributes,
      });
      continue;
    }

    const associationMatch = /^([A-Za-z0-9_]+)\s+(?:"((?:\\"|[^"])*)"\s+)?(\*--|o--|--)\s+(?:"((?:\\"|[^"])*)"\s+)?([A-Za-z0-9_]+)\s*:\s*(.+)$/.exec(line);
    if (associationMatch !== null) {
      associations.push({
        sourceId: associationMatch[1],
        sourceEndLabel: unescapePlantUmlText(associationMatch[2] ?? ""),
        operator: associationMatch[3],
        targetEndLabel: unescapePlantUmlText(associationMatch[4] ?? ""),
        targetId: associationMatch[5],
        label: unescapePlantUmlText(associationMatch[6]),
      });
    }
  }

  return { title, classes, associations };
}

function layoutClasses(classes: ParsedClass[]): LayoutClass[] {
  const classWidth = 220;
  const baseHeight = 58;
  const attributeHeight = 22;
  const gapX = 100;
  const gapY = 86;
  const columns = Math.max(1, Math.min(3, Math.ceil(Math.sqrt(classes.length))));

  return classes.map((item, index) => {
    const row = Math.floor(index / columns);
    const column = index % columns;
    return {
      ...item,
      x: 48 + column * (classWidth + gapX),
      y: 56 + row * (baseHeight + attributeHeight * Math.max(1, item.attributes.length) + gapY),
      width: classWidth,
      height: baseHeight + attributeHeight * item.attributes.length,
    };
  });
}

function appendClassBox(layer: SVGElement, item: LayoutClass): void {
  const group = document.createElementNS(SVG_NS, "g");
  const rect = document.createElementNS(SVG_NS, "rect");
  rect.setAttribute("x", `${item.x}`);
  rect.setAttribute("y", `${item.y}`);
  rect.setAttribute("width", `${item.width}`);
  rect.setAttribute("height", `${item.height}`);
  rect.setAttribute("rx", "3");
  rect.setAttribute("fill", "#f8fafc");
  rect.setAttribute("stroke", "#536579");
  rect.setAttribute("stroke-width", "1.4");
  group.appendChild(rect);

  const headerLine = document.createElementNS(SVG_NS, "line");
  headerLine.setAttribute("x1", `${item.x}`);
  headerLine.setAttribute("y1", `${item.y + 36}`);
  headerLine.setAttribute("x2", `${item.x + item.width}`);
  headerLine.setAttribute("y2", `${item.y + 36}`);
  headerLine.setAttribute("stroke", "#536579");
  group.appendChild(headerLine);

  appendText(group, item.x + item.width / 2, item.y + 23, item.label, {
    anchor: "middle",
    weight: "700",
    fill: "#172033",
  });

  item.attributes.forEach((attribute, index) => {
    appendText(group, item.x + 14, item.y + 58 + index * 22, attribute, {
      fill: "#344255",
    });
  });

  layer.appendChild(group);
}

function appendAssociation(edgeLayer: SVGElement, labelsLayer: SVGElement, sourceClass: LayoutClass, targetClass: LayoutClass, association: ParsedAssociation): void {
  const source = centerOf(sourceClass);
  const target = centerOf(targetClass);
  const line = document.createElementNS(SVG_NS, "line");
  line.setAttribute("x1", `${source.x}`);
  line.setAttribute("y1", `${source.y}`);
  line.setAttribute("x2", `${target.x}`);
  line.setAttribute("y2", `${target.y}`);
  line.setAttribute("stroke-dasharray", association.operator === "o--" ? "6 4" : "");
  edgeLayer.appendChild(line);

  const midpoint = {
    x: (source.x + target.x) / 2,
    y: (source.y + target.y) / 2,
  };
  appendText(labelsLayer, midpoint.x, midpoint.y - 8, association.label, {
    anchor: "middle",
    fill: "#243044",
  });
  if (association.sourceEndLabel) {
    appendText(labelsLayer, source.x + 10, source.y - 10, association.sourceEndLabel, { fill: "#536579" });
  }
  if (association.targetEndLabel) {
    appendText(labelsLayer, target.x - 10, target.y - 10, association.targetEndLabel, {
      anchor: "end",
      fill: "#536579",
    });
  }
}

function centerOf(item: LayoutClass): { x: number; y: number } {
  return {
    x: item.x + item.width / 2,
    y: item.y + item.height / 2,
  };
}

function appendText(
  layer: SVGElement,
  x: number,
  y: number,
  value: string,
  options: { anchor?: "start" | "middle" | "end"; fill?: string; weight?: string } = {},
): void {
  const text = document.createElementNS(SVG_NS, "text");
  text.setAttribute("x", `${x}`);
  text.setAttribute("y", `${y}`);
  text.setAttribute("fill", options.fill ?? "#344255");
  text.setAttribute("font-weight", options.weight ?? "400");
  if (options.anchor !== undefined) {
    text.setAttribute("text-anchor", options.anchor);
  }
  text.textContent = value;
  layer.appendChild(text);
}

function unescapePlantUmlText(value: string): string {
  const trimmed = value.trim();
  const unquoted = trimmed.startsWith("\"") && trimmed.endsWith("\"")
    ? trimmed.slice(1, -1)
    : trimmed;
  return unquoted.replace(/\\"/g, "\"").replace(/\\\\/g, "\\");
}
