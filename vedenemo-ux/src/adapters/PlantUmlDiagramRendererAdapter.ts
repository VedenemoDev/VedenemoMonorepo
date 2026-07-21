import "@plantuml/core/viz-global.js";
import { renderToString } from "@plantuml/core";

export class PlantUmlDiagramRendererAdapter {
  renderSvg(source: string): Promise<string> {
    return new Promise((resolve, reject) => {
      renderToString(
        source.split(/\r?\n/),
        (svg) => resolve(svg),
        (message) => reject(new Error(message)),
      );
    });
  }
}
