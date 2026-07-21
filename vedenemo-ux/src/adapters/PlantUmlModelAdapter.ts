type EntitySummary = {
  azName: string;
  visName: string;
  activeSince: string;
  deprecatedSince: string | null;
};

type AttributeSummary = {
  azName: string;
  visName: string;
  dataType: string;
  activeSince: string;
  deprecatedSince: string | null;
};

export class PlantUmlModelAdapter {
  async renderModel(apiBaseUrl: string, modelAzName: string): Promise<string> {
    const entities = await fetchJson<EntitySummary[]>(`${apiBaseUrl}/models/${encodeURIComponent(modelAzName)}/entities`);
    const lines = [
      "@startuml",
      `title ${plantUmlText(modelAzName)}`,
      "",
    ];

    for (const entity of entities) {
      lines.push(`class ${identifier(entity.azName)} as "${plantUmlText(entity.visName)}" {`);
      lines.push(`  .. ${plantUmlText(entity.azName)} ..`);
      lines.push(`  activeSince : ${plantUmlText(entity.activeSince)}`);
      if (entity.deprecatedSince !== null) {
        lines.push(`  deprecatedSince : ${plantUmlText(entity.deprecatedSince)}`);
      }

      const attributes = await fetchJson<AttributeSummary[]>(
        `${apiBaseUrl}/models/${encodeURIComponent(modelAzName)}/entities/${encodeURIComponent(entity.azName)}/attributes`,
      );

      for (const attribute of attributes) {
        lines.push(`  ${plantUmlText(attribute.azName)} : ${plantUmlText(attribute.dataType)}`);
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
