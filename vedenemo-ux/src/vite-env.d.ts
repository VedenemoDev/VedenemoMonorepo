/// <reference types="vite/client" />

declare module "@plantuml/core" {
  export function render(
    lines: string[],
    targetId: string,
    options?: { dark?: boolean },
  ): void;
}

declare module "@plantuml/core/viz-global.js";
