import { useEffect, useState } from "react";

interface MappingRow {
  field: string;
  jsonPath: string;
}

interface Props {
  value: string;
  onChange: (template: string) => void;
}

const PLACEHOLDER_RE = /^\$\{(.+)\}$/;

function parseTemplate(template: string): { rows: MappingRow[]; parseable: boolean } {
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

function buildTemplate(rows: MappingRow[]): string {
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

/**
 * Visual editor for Subscription.targetPayloadTemplate — this is the field-mapping step of
 * RelayHub's core design (Canonical Event -> Target payload, see docs/architecture/system-design.md
 * "JsonNode-tree payload mapping") and a raw JSON textarea alone doesn't make that mapping
 * relationship (target field <- source JSONPath) visually obvious. Falls back to a raw-JSON
 * textarea for anything this row-based view can't represent (nested objects, arrays) — the
 * underlying template format and backend contract are unchanged either way.
 */
export function MappingBuilder({ value, onChange }: Props) {
  const [rows, setRows] = useState<MappingRow[]>([]);
  const [advanced, setAdvanced] = useState(false);

  useEffect(() => {
    const { rows: parsed, parseable } = parseTemplate(value);
    setRows(parsed);
    if (!parseable && value) setAdvanced(true);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  function update(rows: MappingRow[]) {
    setRows(rows);
    onChange(buildTemplate(rows));
  }

  function addRow() {
    update([...rows, { field: "", jsonPath: "" }]);
  }

  function updateRow(index: number, patch: Partial<MappingRow>) {
    update(rows.map((r, i) => (i === index ? { ...r, ...patch } : r)));
  }

  function removeRow(index: number) {
    update(rows.filter((_, i) => i !== index));
  }

  if (advanced) {
    return (
      <div className="mapping-builder">
        <div className="mapping-builder-header">
          <span>Target payload template (raw JSON)</span>
          <button type="button" className="link-button" onClick={() => setAdvanced(false)}>
            Switch to field mapping
          </button>
        </div>
        <textarea value={value} onChange={(e) => onChange(e.target.value)} rows={4} />
      </div>
    );
  }

  return (
    <div className="mapping-builder">
      <div className="mapping-builder-header">
        <span>Field mapping (target field &larr; source JSONPath)</span>
        <button type="button" className="link-button" onClick={() => setAdvanced(true)}>
          Edit raw JSON
        </button>
      </div>
      {rows.map((row, i) => (
        <div className="mapping-row" key={i}>
          <input
            placeholder="targetField"
            value={row.field}
            onChange={(e) => updateRow(i, { field: e.target.value })}
          />
          <span className="mapping-arrow">&larr;</span>
          <input
            placeholder="$.sourceField or a literal value"
            value={row.jsonPath}
            onChange={(e) => updateRow(i, { jsonPath: e.target.value })}
          />
          <button type="button" onClick={() => removeRow(i)} aria-label="Remove field">
            &times;
          </button>
        </div>
      ))}
      <button type="button" onClick={addRow}>
        + Add field
      </button>
      {rows.length === 0 && <p className="muted">No fields yet — add at least one.</p>}
    </div>
  );
}
