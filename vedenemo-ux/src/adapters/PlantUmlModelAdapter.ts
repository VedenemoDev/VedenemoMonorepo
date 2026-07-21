type EntitySummary = {
  azName: string;
  visName: string;
};

type AttributeSummary = {
  visName: string;
  dataType: string;
};

export class PlantUmlModelAdapter {
  async renderModel(apiBaseUrl: string, modelAzName: string): Promise<string> {
    const entities = await fetchJson<EntitySummary[]>(`${apiBaseUrl}/models/${encodeURIComponent(modelAzName)}/entities`);
    const lines = [
      "@startuml",
      "hide circle",
      `title ${plantUmlText(modelAzName)}`,
      "",
    ];

    for (const entity of entities) {
      lines.push(`class ${identifier(entity.azName)} as "${plantUmlText(entity.visName)}" {`);
      const attributes = await fetchJson<AttributeSummary[]>(
        `${apiBaseUrl}/models/${encodeURIComponent(modelAzName)}/entities/${encodeURIComponent(entity.azName)}/attributes`,
      );

      for (const attribute of attributes) {
        lines.push(`  ${plantUmlText(attribute.visName)} : ${plantUmlText(attribute.dataType)}`);
      }

      lines.push("}");
      lines.push("");
    }

    lines.push("@enduml");
    return lines.join("\n");
  }
}

async function fetchJson<T>(url: string): Promise<T> {
  const response = await fetch(url, {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  return response.json() as Promise<T>;
}

function identifier(value: string): string {
  return value.replace(/[^A-Za-z0-9_]/g, "_");
}

function plantUmlText(value: string): string {
  return value.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
}
