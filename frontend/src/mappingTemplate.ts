/** Extracted from MappingBuilder.tsx (self-review finding, 2026-09-17) so this parsing/building
 *  logic — genuinely easy to get subtly wrong (the "$"-prefix-means-JSONPath convention, escaping
 *  a literal that happens to look like one) — can be unit tested without rendering the component. */

export interface MappingRow {
  field: string;
  jsonPath: string;
}

const PLACEHOLDER_RE = /^\$\{(.+)\}$/;

export function parseTemplate(template: string): { rows: MappingRow[]; parseable: boolean } {
  try {
    const obj = JSON.parse(template || "{}");
    if (typeof obj !== "object" || obj === null || Array.isArray(obj)) {
      return { rows: [], parseable: false };
    }
    const rows = Object.entries(obj).map(([field, raw]) => {
      const match = typeof raw === "string" ? raw.match(PLACEHOLDER_RE) : null;
      return { field, jsonPath: match ? match[1] : String(raw) };
    });
    return { rows, parseable: true };
  } catch {
    return { rows: [], parseable: false };
  }
}

export function buildTemplate(rows: MappingRow[]): string {
  const obj: Record<string, string> = {};
  for (const row of rows) {
    if (!row.field) continue;
    // A bare "$.foo" or "$.foo.bar" reads as a JSONPath extraction; anything else (a literal
    // like "true" or "fixed-value") is written through as-is, matching how existing templates
    // in this codebase mix ${$.jsonpath} extractions with occasional literal fields.
    obj[row.field] = row.jsonPath.trim().startsWith("$") ? `\${${row.jsonPath.trim()}}` : row.jsonPath;
  }
  return JSON.stringify(obj);
}
