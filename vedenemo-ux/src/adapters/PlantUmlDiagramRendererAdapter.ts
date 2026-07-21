import "@plantuml/core/viz-global.js";
import { render } from "@plantuml/core";

export class PlantUmlDiagramRendererAdapter {
  renderSvg(source: string, targetId: string): Promise<void> {
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
