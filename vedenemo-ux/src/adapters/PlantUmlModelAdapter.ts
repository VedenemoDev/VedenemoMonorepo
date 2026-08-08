type EntitySummary = {
  azName: string;
  visName: string;
};

type AttributeSummary = {
  visName: string;
  dataType: string;
};

type AssociationSummary = {
  visName: string;
  kind: "OWNERSHIP" | "REFERENCE" | "RELATION";
  sourceEntityAzName: string;
  targetEntityAzName: string;
  cardinality: string;
  sourceRoleName: string | null;
  targetRoleName: string | null;
  sourceCardinality: string | null;
  targetCardinality: string | null;
};

export class PlantUmlModelAdapter {
  async renderModel(apiBaseUrl: string, modelAzName: string, modelVisName = modelAzName): Promise<string> {
    const entities = await fetchJson<EntitySummary[]>(`${apiBaseUrl}/models/${encodeURIComponent(modelAzName)}/entities`);
    const associations = await fetchJson<AssociationSummary[]>(
      `${apiBaseUrl}/models/${encodeURIComponent(modelAzName)}/associations`,
    );
    const lines = [
      "@startuml",
      "hide circle",
      "hide empty members",
      `title ${plantUmlText(modelVisName)}`,
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

    for (const association of associations) {
      lines.push(associationLine(association));
    }

    if (associations.length > 0) {
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

function associationOperator(kind: AssociationSummary["kind"]): string {
  switch (kind) {
    case "OWNERSHIP":
      return "*--";
    case "REFERENCE":
      return "o--";
    case "RELATION":
      return "--";
  }
}

function associationLine(association: AssociationSummary): string {
  if (association.kind === "RELATION") {
    const sourceLabel = association.sourceRoleName === null ? "" : `"${plantUmlText(association.sourceRoleName)} ${plantUmlText(association.sourceCardinality ?? "")}" `;
    const targetLabel = association.targetRoleName === null ? "" : ` "${plantUmlText(association.targetRoleName)} ${plantUmlText(association.targetCardinality ?? "")}"`;
    return `${identifier(association.sourceEntityAzName)} ${sourceLabel}${associationOperator(association.kind)}${targetLabel} ${identifier(
      association.targetEntityAzName,
    )} : "${plantUmlText(association.visName)}"`;
  }
  return `${identifier(association.sourceEntityAzName)} ${associationOperator(association.kind)} ${identifier(
    association.targetEntityAzName,
  )} : "${plantUmlText(association.visName)}" ${plantUmlText(association.cardinality)}`;
}
