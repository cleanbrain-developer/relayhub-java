import { FormEvent, useEffect, useState } from "react";
import { del, get, post, put } from "../api";
import { FieldDataType, Status } from "../types";

const DATA_TYPES: FieldDataType[] = ["STRING", "NUMBER", "BOOLEAN", "OBJECT", "ARRAY", "DATE"];

interface FieldRow {
  key: string;
  jsonPath?: string;
  dataType: FieldDataType;
  description: string | null;
  exampleValue: string | null;
  required: boolean;
  sensitive: boolean;
  status: Status;
}

const emptyForm = {
  key: "",
  jsonPath: "",
  dataType: "STRING" as FieldDataType,
  description: "",
  exampleValue: "",
  required: false,
  sensitive: false,
};

interface Props {
  /** e.g. `/api/sources/demo-flightstatus/events/flight-created/fields` or `/api/targets/demo-airport-display/fields` */
  basePath: string;
  /** SourceField has jsonPath (required, scoped to one event's payload); TargetField doesn't. */
  includeJsonPath: boolean;
  loggedIn: boolean;
}

/**
 * Spec 006 (specs/006-field-registry/spec.md): registers the fields a Source Event can supply or
 * a Target can accept, so Subscription's MappingBuilder can offer them as dropdown choices instead
 * of requiring free-typed JSONPath/field-name text. Shared between SourcesPage (nested under each
 * Source Event) and TargetsPage (nested under each Target) — the only real difference between the
 * two is whether jsonPath is collected.
 */
export function FieldRegistryEditor({ basePath, includeJsonPath, loggedIn }: Props) {
  const [fields, setFields] = useState<FieldRow[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [showInactive, setShowInactive] = useState(false);
  const [form, setForm] = useState(emptyForm);

  function reload() {
    get<FieldRow[]>(basePath).then(setFields).catch((err) => setError((err as Error).message));
  }

  useEffect(reload, [basePath]);

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const { jsonPath, ...rest } = form;
      await post(basePath, includeJsonPath ? { jsonPath, ...rest } : rest);
      setForm(emptyForm);
      setShowCreate(false);
      reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  async function toggleRequired(field: FieldRow, required: boolean) {
    setError(null);
    try {
      const { key, status, ...rest } = field;
      await put(`${basePath}/${field.key}`, { ...rest, required });
      reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  async function deactivate(key: string) {
    if (!confirm(`Deactivate field "${key}"?`)) return;
    setError(null);
    try {
      await del(`${basePath}/${key}`);
      reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  async function hardDelete(key: string) {
    if (!confirm(`Permanently delete field "${key}"? This cannot be undone.`)) return;
    setError(null);
    try {
      await del(`${basePath}/${key}?hard=true`);
      reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  const visible = fields.filter((f) => showInactive || f.status === "ACTIVE");

  return (
    <div className="field-registry">
      {error && <p className="error">{error}</p>}
      <div className="field-registry-header">
        <label className="inline-checkbox">
          <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
          Show deactivated
        </label>
        {loggedIn && (
          <button type="button" className="link-button" onClick={() => setShowCreate(!showCreate)}>
            {showCreate ? "Cancel" : "+ Add field"}
          </button>
        )}
      </div>

      {showCreate && loggedIn && (
        <form onSubmit={handleCreate} className="field-registry-form">
          <input
            required
            placeholder="key (e.g. customerNo)"
            value={form.key}
            onChange={(e) => setForm({ ...form, key: e.target.value })}
          />
          {includeJsonPath && (
            <input
              required
              placeholder="$.customerNo"
              value={form.jsonPath}
              onChange={(e) => setForm({ ...form, jsonPath: e.target.value })}
            />
          )}
          <select value={form.dataType} onChange={(e) => setForm({ ...form, dataType: e.target.value as FieldDataType })}>
            {DATA_TYPES.map((t) => (
              <option key={t}>{t}</option>
            ))}
          </select>
          <input
            placeholder="description (optional)"
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
          <input
            placeholder="example value (optional)"
            value={form.exampleValue}
            onChange={(e) => setForm({ ...form, exampleValue: e.target.value })}
          />
          <label className="inline-checkbox">
            <input type="checkbox" checked={form.required} onChange={(e) => setForm({ ...form, required: e.target.checked })} />
            Required
          </label>
          <label className="inline-checkbox">
            <input
              type="checkbox"
              checked={form.sensitive}
              onChange={(e) => setForm({ ...form, sensitive: e.target.checked })}
            />
            Sensitive
          </label>
          <button type="submit" className="btn-primary">
            Add
          </button>
        </form>
      )}

      {visible.length === 0 ? (
        <p className="muted">
          {fields.length === 0 ? "No fields registered yet." : "No active fields — try “Show deactivated”."}
        </p>
      ) : (
        <table className="nested-table">
          <thead>
            <tr>
              <th>Key</th>
              {includeJsonPath && <th>JSONPath</th>}
              <th>Type</th>
              <th>Example</th>
              <th>Required</th>
              <th>Sensitive</th>
              <th>Status</th>
              {loggedIn && <th />}
            </tr>
          </thead>
          <tbody>
            {visible.map((f) => (
              <tr key={f.key}>
                <td>
                  <code>{f.key}</code>
                </td>
                {includeJsonPath && (
                  <td>
                    <code>{f.jsonPath}</code>
                  </td>
                )}
                <td>{f.dataType}</td>
                <td className="muted">{f.exampleValue ?? "-"}</td>
                <td>
                  {loggedIn ? (
                    <input type="checkbox" checked={f.required} onChange={(e) => toggleRequired(f, e.target.checked)} />
                  ) : f.required ? (
                    "yes"
                  ) : (
                    "no"
                  )}
                </td>
                <td>{f.sensitive ? "yes" : "no"}</td>
                <td>
                  <span className={`badge ${f.status === "ACTIVE" ? "badge-ok" : "badge-muted"}`}>{f.status}</span>
                </td>
                {loggedIn && (
                  <td>
                    {f.status === "ACTIVE" && <button onClick={() => deactivate(f.key)}>Deactivate</button>}
                    <button className="btn-danger" onClick={() => hardDelete(f.key)}>
                      Delete
                    </button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
