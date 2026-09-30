import { FormEvent, useEffect, useState } from "react";
import { del, get, post, put } from "../api";
import { HttpVerb, TargetEndpoint } from "../types";
import { StatusBadge } from "./StatusBadge";
import { FieldRegistryEditor } from "./FieldRegistryEditor";
import { CopyButton } from "./CopyButton";
import { useToast } from "../toast";

interface Props {
  targetKey: string;
  /** For the full-URL copy affordance next to each endpoint (Stage 3) — the Target's own baseUrl,
   *  which this component doesn't otherwise fetch itself. */
  baseUrl: string;
  loggedIn: boolean;
}

const HTTP_VERBS: HttpVerb[] = ["GET", "POST", "PUT", "PATCH", "DELETE"];

interface EndpointFormState {
  key: string;
  name: string;
  description: string;
  httpMethod: HttpVerb;
  path: string;
  timeoutOverrideMs: string;
  headers: string;
}

const emptyForm: EndpointFormState = {
  key: "",
  name: "",
  description: "",
  httpMethod: "POST",
  path: "",
  timeoutOverrideMs: "",
  headers: "",
};

function toFormState(ep: TargetEndpoint): EndpointFormState {
  return {
    key: ep.key,
    name: ep.name,
    description: ep.description,
    httpMethod: ep.httpMethod,
    path: ep.path,
    timeoutOverrideMs: ep.timeoutOverrideMs !== null ? String(ep.timeoutOverrideMs) : "",
    headers: ep.headers ?? "",
  };
}

/**
 * Lists a Target's registered endpoints (one per callable API contract, e.g. "POST /customers"),
 * with full add/edit/deactivate/delete — mirrors SourceEventFields' pattern on the Source side.
 * Each endpoint's field registry (Spec 006 — see FieldRegistryEditor) stays nested underneath,
 * always expanded, same as before this Target/TargetEndpoint split (domain-model overhaul,
 * maintainer request 2026-09-30 — TargetField moved from Target-scoped to TargetEndpoint-scoped
 * since the same Target can expose endpoints with different request shapes).
 */
export function TargetEndpoints({ targetKey, baseUrl, loggedIn }: Props) {
  const [endpoints, setEndpoints] = useState<TargetEndpoint[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [showInactive, setShowInactive] = useState(false);
  const [form, setForm] = useState<EndpointFormState>(emptyForm);
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [editForm, setEditForm] = useState<EndpointFormState>(emptyForm);
  const { notify } = useToast();

  function reload() {
    get<TargetEndpoint[]>(`/api/targets/${targetKey}/endpoints`)
      .then(setEndpoints)
      .catch((err) => setError((err as Error).message))
      .finally(() => setLoading(false));
  }

  useEffect(reload, [targetKey]);

  function toRequestBody(state: EndpointFormState) {
    return {
      name: state.name,
      description: state.description,
      httpMethod: state.httpMethod,
      path: state.path,
      timeoutOverrideMs: state.timeoutOverrideMs ? Number(state.timeoutOverrideMs) : null,
      headers: state.headers || null,
    };
  }

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await post(`/api/targets/${targetKey}/endpoints`, { key: form.key, ...toRequestBody(form) });
      setForm(emptyForm);
      setShowCreate(false);
      reload();
      notify(`Target Endpoint "${form.key}" created.`);
    } catch (err) {
      setError((err as Error).message);
      notify((err as Error).message, "error");
    }
  }

  function startEdit(ep: TargetEndpoint) {
    if (editingKey === ep.key) {
      setEditingKey(null);
      return;
    }
    setEditingKey(ep.key);
    setEditForm(toFormState(ep));
  }

  async function saveEdit(key: string) {
    setError(null);
    try {
      await put(`/api/targets/${targetKey}/endpoints/${key}`, toRequestBody(editForm));
      setEditingKey(null);
      reload();
      notify(`Target Endpoint "${key}" updated.`);
    } catch (err) {
      setError((err as Error).message);
      notify((err as Error).message, "error");
    }
  }

  async function deactivate(key: string) {
    if (!confirm(`Deactivate Target Endpoint "${key}"? Existing Subscriptions/Deliveries referencing it are kept.`)) return;
    setError(null);
    try {
      await del(`/api/targets/${targetKey}/endpoints/${key}`);
      reload();
      notify(`Target Endpoint "${key}" deactivated.`);
    } catch (err) {
      setError((err as Error).message);
      notify((err as Error).message, "error");
    }
  }

  async function hardDelete(key: string) {
    if (
      !confirm(`Permanently delete Target Endpoint "${key}"? This cannot be undone. Blocked if any Subscription still uses it.`)
    )
      return;
    setError(null);
    try {
      await del(`/api/targets/${targetKey}/endpoints/${key}?hard=true`);
      reload();
      notify(`Target Endpoint "${key}" permanently deleted.`);
    } catch (err) {
      setError((err as Error).message);
      notify((err as Error).message, "error");
    }
  }

  function renderEndpointFields(state: EndpointFormState, setState: (s: EndpointFormState) => void, keyEditable: boolean) {
    return (
      <div className="form-grid">
        <label>
          Key
          <input
            required
            disabled={!keyEditable}
            value={state.key}
            onChange={(e) => setState({ ...state, key: e.target.value })}
          />
        </label>
        <label>
          Name
          <input required value={state.name} onChange={(e) => setState({ ...state, name: e.target.value })} />
        </label>
        <label className="form-wide">
          Description
          <input
            required
            value={state.description}
            onChange={(e) => setState({ ...state, description: e.target.value })}
          />
        </label>
        <label>
          HTTP method
          <select value={state.httpMethod} onChange={(e) => setState({ ...state, httpMethod: e.target.value as HttpVerb })}>
            {HTTP_VERBS.map((m) => (
              <option key={m}>{m}</option>
            ))}
          </select>
        </label>
        <label>
          Path
          <input
            required
            placeholder="/customers"
            value={state.path}
            onChange={(e) => setState({ ...state, path: e.target.value })}
          />
        </label>
        <label>
          Timeout override (ms, optional)
          <input
            type="number"
            min={1}
            value={state.timeoutOverrideMs}
            onChange={(e) => setState({ ...state, timeoutOverrideMs: e.target.value })}
          />
        </label>
        <label className="form-wide">
          Extra headers (optional, free text)
          <textarea rows={2} value={state.headers} onChange={(e) => setState({ ...state, headers: e.target.value })} />
        </label>
      </div>
    );
  }

  const visible = endpoints.filter((ep) => showInactive || ep.status === "ACTIVE");

  return (
    <div className="source-event-fields">
      {error && <p className="error">{error}</p>}
      <div className="field-registry-header">
        <label className="inline-checkbox">
          <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
          Show deactivated endpoints
        </label>
        {loggedIn && (
          <button type="button" className="link-button" onClick={() => setShowCreate(!showCreate)}>
            {showCreate ? "Cancel" : "+ New Target Endpoint"}
          </button>
        )}
      </div>

      {showCreate && loggedIn && (
        <form onSubmit={handleCreate} className="card form-card">
          {renderEndpointFields(form, setForm, true)}
          <button type="submit" className="btn-primary">
            Create Target Endpoint
          </button>
        </form>
      )}

      {loading && <p className="muted">Loading Target Endpoints...</p>}
      {!loading && visible.length === 0 && (
        <p className="muted">
          {endpoints.length === 0
            ? "No Target Endpoints registered for this Target yet."
            : "No active Target Endpoints — try “Show deactivated endpoints”."}
        </p>
      )}

      {visible.map((ep) => (
        <div key={ep.key} className="source-event-row">
          <div className="source-event-row-header">
            <div>
              <code>{ep.key}</code>
              <span className="muted">
                {" "}
                &middot; {ep.name} &middot; {ep.httpMethod} <code>{ep.path}</code>
              </span>
              <CopyButton value={`${baseUrl}${ep.path}`} />
            </div>
            <div className="entity-card-actions">
              <StatusBadge value={ep.status} />
              {loggedIn && (
                <>
                  <button onClick={() => startEdit(ep)}>{editingKey === ep.key ? "Close" : "Edit"}</button>
                  {ep.status === "ACTIVE" && <button onClick={() => deactivate(ep.key)}>Deactivate</button>}
                  <button className="btn-danger" onClick={() => hardDelete(ep.key)}>
                    Delete permanently
                  </button>
                </>
              )}
            </div>
          </div>
          {editingKey === ep.key && (
            <div className="entity-card-edit">
              {renderEndpointFields(editForm, setEditForm, false)}
              <button type="button" className="btn-primary" onClick={() => saveEdit(ep.key)}>
                Save
              </button>
            </div>
          )}
          <FieldRegistryEditor
            basePath={`/api/targets/${targetKey}/endpoints/${ep.key}/fields`}
            includeJsonPath={false}
            loggedIn={loggedIn}
          />
        </div>
      ))}
    </div>
  );
}
