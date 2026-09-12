import { Fragment, FormEvent, useEffect, useState } from "react";
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

interface FieldFormState {
  key: string;
  jsonPath: string;
  dataType: FieldDataType;
  description: string;
  exampleValue: string;
  required: boolean;
  sensitive: boolean;
}

const emptyForm: FieldFormState = {
  key: "",
  jsonPath: "",
  dataType: "STRING",
  description: "",
  exampleValue: "",
  required: false,
  sensitive: false,
};

function toFormState(f: FieldRow): FieldFormState {
  return {
    key: f.key,
    jsonPath: f.jsonPath ?? "",
    dataType: f.dataType,
    description: f.description ?? "",
    exampleValue: f.exampleValue ?? "",
    required: f.required,
    sensitive: f.sensitive,
  };
}

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
 *
 * Full add/edit/delete, not just add+delete: an earlier version only let the "required" checkbox
 * be toggled inline, with no way to fix a typo'd jsonPath/description/exampleValue/dataType/
 * sensitive flag short of deleting and recreating the field — the maintainer asked for this
 * explicitly (2026-09-12) after noticing edit was incomplete.
 */
export function FieldRegistryEditor({ basePath, includeJsonPath, loggedIn }: Props) {
  const [fields, setFields] = useState<FieldRow[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [showInactive, setShowInactive] = useState(false);
  const [form, setForm] = useState<FieldFormState>(emptyForm);
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [editForm, setEditForm] = useState<FieldFormState>(emptyForm);

  function reload() {
    get<FieldRow[]>(basePath).then(setFields).catch((err) => setError((err as Error).message));
  }

  useEffect(reload, [basePath]);

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const { key, jsonPath, ...rest } = form;
      await post(basePath, includeJsonPath ? { key, jsonPath, ...rest } : { key, ...rest });
      setForm(emptyForm);
      setShowCreate(false);
      reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  function startEdit(f: FieldRow) {
    if (editingKey === f.key) {
      setEditingKey(null);
      return;
    }
    setEditingKey(f.key);
    setEditForm(toFormState(f));
  }

  async function saveEdit(key: string) {
    setError(null);
    try {
      const { key: _key, jsonPath, ...rest } = editForm;
      await put(`${basePath}/${key}`, includeJsonPath ? { jsonPath, ...rest } : rest);
      setEditingKey(null);
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

  function renderFields(state: FieldFormState, setState: (s: FieldFormState) => void, keyEditable: boolean) {
    return (
      <>
        <input
          required
          disabled={!keyEditable}
          placeholder="key (e.g. customerNo)"
          value={state.key}
          onChange={(e) => setState({ ...state, key: e.target.value })}
        />
        {includeJsonPath && (
          <input
            required
            placeholder="$.customerNo"
            value={state.jsonPath}
            onChange={(e) => setState({ ...state, jsonPath: e.target.value })}
          />
        )}
        <select value={state.dataType} onChange={(e) => setState({ ...state, dataType: e.target.value as FieldDataType })}>
          {DATA_TYPES.map((t) => (
            <option key={t}>{t}</option>
          ))}
        </select>
        <input
          placeholder="description (optional)"
          value={state.description}
          onChange={(e) => setState({ ...state, description: e.target.value })}
        />
        <input
          placeholder="example value (optional)"
          value={state.exampleValue}
          onChange={(e) => setState({ ...state, exampleValue: e.target.value })}
        />
        <label className="inline-checkbox">
          <input type="checkbox" checked={state.required} onChange={(e) => setState({ ...state, required: e.target.checked })} />
          Required
        </label>
        <label className="inline-checkbox">
          <input type="checkbox" checked={state.sensitive} onChange={(e) => setState({ ...state, sensitive: e.target.checked })} />
          Sensitive
        </label>
      </>
    );
  }

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
          {renderFields(form, setForm, true)}
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
              <Fragment key={f.key}>
                <tr>
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
                  <td>{f.required ? "yes" : "no"}</td>
                  <td>{f.sensitive ? "yes" : "no"}</td>
                  <td>
                    <span className={`badge ${f.status === "ACTIVE" ? "badge-ok" : "badge-muted"}`}>{f.status}</span>
                  </td>
                  {loggedIn && (
                    <td>
                      <button onClick={() => startEdit(f)}>{editingKey === f.key ? "Close" : "Edit"}</button>
                      {f.status === "ACTIVE" && <button onClick={() => deactivate(f.key)}>Deactivate</button>}
                      <button className="btn-danger" onClick={() => hardDelete(f.key)}>
                        Delete
                      </button>
                    </td>
                  )}
                </tr>
                {editingKey === f.key && (
                  <tr>
                    <td colSpan={(includeJsonPath ? 7 : 6) + (loggedIn ? 1 : 0)}>
                      <div className="field-registry-form field-registry-edit-form">
                        {renderFields(editForm, setEditForm, false)}
                        <button type="button" className="btn-primary" onClick={() => saveEdit(f.key)}>
                          Save
                        </button>
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
