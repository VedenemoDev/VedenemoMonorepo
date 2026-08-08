import vizGlobalUrl from "@plantuml/core/viz-global.js?url";

type PlantUmlModule = typeof import("@plantuml/core");

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
      const { renderToString } = await loadPlantUmlModule();

      const target = document.getElementById(targetId);
      if (target === null) {
        throw new Error("diagram target is unavailable");
      }

      target.replaceChildren();

      const svg = await new Promise<string>((resolve, reject) => {
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

      const parsedSvg = new DOMParser().parseFromString(svg, "image/svg+xml").documentElement;
      if (parsedSvg.tagName.toLocaleLowerCase() !== "svg") {
        throw new Error("PlantUML renderer returned invalid SVG");
      }

      target.replaceChildren(parsedSvg);
    } finally {
      releaseRender();
    }
  }
}
