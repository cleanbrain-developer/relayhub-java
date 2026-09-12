import { useEffect, useState } from "react";
import { get } from "../api";
import { SourceField, TargetField } from "../types";

interface MappingRow {
  field: string;
  jsonPath: string;
}

interface Props {
  value: string;
  onChange: (template: string) => void;
  /** When all three are known, rows offer a dropdown pulling from the registered SourceField/
   *  TargetField lists (Spec 006) instead of pure free text. Any of them missing (e.g. the create
   *  form before a Source/Event/Target is picked) just falls back to free text, same as before. */
  sourceKey?: string;
  sourceEventKey?: string;
  targetKey?: string;
}

const PLACEHOLDER_RE = /^\$\{(.+)\}$/;
const CUSTOM = "__custom__";

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
 *
 * Each row's two sides are dropdowns sourced from the Spec 006 field registries (SourceField for
 * the right-hand JSONPath side, TargetField for the left-hand target-field side) when the current
 * Source Event/Target are known — see specs/006-field-registry/spec.md "Subscription mapping
 * becomes a selection, not free text". Anything not yet registered still falls back to the same
 * free-text input as before ("Custom..." in the dropdown).
 */
export function MappingBuilder({ value, onChange, sourceKey, sourceEventKey, targetKey }: Props) {
  const [rows, setRows] = useState<MappingRow[]>([]);
  const [advanced, setAdvanced] = useState(false);
  const [sourceFields, setSourceFields] = useState<SourceField[]>([]);
  const [targetFields, setTargetFields] = useState<TargetField[]>([]);

  useEffect(() => {
    const { rows: parsed, parseable } = parseTemplate(value);
    setRows(parsed);
    if (!parseable && value) setAdvanced(true);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!sourceKey || !sourceEventKey) {
      setSourceFields([]);
      return;
    }
    get<SourceField[]>(`/api/sources/${sourceKey}/events/${sourceEventKey}/fields`)
      .then((all) => setSourceFields(all.filter((f) => f.status === "ACTIVE")))
      .catch(() => setSourceFields([]));
  }, [sourceKey, sourceEventKey]);

  useEffect(() => {
    if (!targetKey) {
      setTargetFields([]);
      return;
    }
    get<TargetField[]>(`/api/targets/${targetKey}/fields`)
      .then((all) => setTargetFields(all.filter((f) => f.status === "ACTIVE")))
      .catch(() => setTargetFields([]));
  }, [targetKey]);

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
      {rows.map((row, i) => {
        const matchedTargetField = targetFields.find((f) => f.key === row.field);
        const matchedSourceField = sourceFields.find((f) => f.jsonPath === row.jsonPath);
        return (
          <div className="mapping-row" key={i}>
            {targetFields.length > 0 ? (
              <>
                <select
                  className="mapping-select"
                  value={matchedTargetField ? matchedTargetField.key : CUSTOM}
                  onChange={(e) => updateRow(i, { field: e.target.value === CUSTOM ? "" : e.target.value })}
                >
                  <option value={CUSTOM}>Custom...</option>
                  {targetFields.map((f) => (
                    <option key={f.key} value={f.key}>
                      {f.key}
                      {f.required ? " *" : ""}
                    </option>
                  ))}
                </select>
                {!matchedTargetField && (
                  <input
                    placeholder="targetField"
                    value={row.field}
                    onChange={(e) => updateRow(i, { field: e.target.value })}
                  />
                )}
              </>
            ) : (
              <input
                placeholder="targetField"
                value={row.field}
                onChange={(e) => updateRow(i, { field: e.target.value })}
              />
            )}
            <span className="mapping-arrow">&larr;</span>
            {sourceFields.length > 0 ? (
              <>
                <select
                  className="mapping-select"
                  value={matchedSourceField ? matchedSourceField.key : CUSTOM}
                  onChange={(e) => {
                    const chosen = sourceFields.find((f) => f.key === e.target.value);
                    updateRow(i, { jsonPath: chosen ? chosen.jsonPath : "" });
                  }}
                >
                  <option value={CUSTOM}>Custom...</option>
                  {sourceFields.map((f) => (
                    <option key={f.key} value={f.key}>
                      {f.key} ({f.jsonPath})
                    </option>
                  ))}
                </select>
                {!matchedSourceField && (
                  <input
                    placeholder="$.sourceField or a literal value"
                    value={row.jsonPath}
                    onChange={(e) => updateRow(i, { jsonPath: e.target.value })}
                  />
                )}
              </>
            ) : (
              <input
                placeholder="$.sourceField or a literal value"
                value={row.jsonPath}
                onChange={(e) => updateRow(i, { jsonPath: e.target.value })}
              />
            )}
            <button type="button" onClick={() => removeRow(i)} aria-label="Remove field">
              &times;
            </button>
          </div>
        );
      })}
      <button type="button" onClick={addRow}>
        + Add field
      </button>
      {rows.length === 0 && <p className="muted">No fields yet — add at least one.</p>}
    </div>
  );
}
