import vizGlobalUrl from "@plantuml/core/viz-global.js?url";

type PlantUmlModule = typeof import("@plantuml/core");

let plantUmlModulePromise: Promise<PlantUmlModule> | null = null;

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
    const { render } = await loadPlantUmlModule();

    return new Promise((resolve, reject) => {
      const target = document.getElementById(targetId);
      if (target === null) {
        reject(new Error("diagram target is unavailable"));
        return;
      }

      target.replaceChildren();

      let timeoutId = 0;

      const observer = new MutationObserver(() => {
        if (target.querySelector("svg") === null) {
          return;
        }
        window.clearTimeout(timeoutId);
        observer.disconnect();
        resolve();
      });

      observer.observe(target, {
        childList: true,
        subtree: true,
      });

      timeoutId = window.setTimeout(() => {
        observer.disconnect();
        reject(new Error("PlantUML renderer did not complete"));
      }, 10000);

      try {
        render(source.split(/\r?\n/), targetId);
      } catch (error) {
        window.clearTimeout(timeoutId);
        observer.disconnect();
        reject(error instanceof Error ? error : new Error("PlantUML renderer failed"));
        return;
      }

      if (target.querySelector("svg") !== null) {
        window.clearTimeout(timeoutId);
        observer.disconnect();
        resolve();
      }
    });
  }
}
