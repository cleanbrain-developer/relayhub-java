import { FormEvent, useEffect, useState } from "react";
import { del, get, post, put } from "../api";
import { isLoggedIn } from "../auth";
import { Source } from "../types";
import { StatusBadge } from "../components/StatusBadge";
import { SourceEventFields } from "../components/SourceEventFields";
import { useToast } from "../toast";

const emptyForm = { key: "", name: "", description: "", authenticationConfig: "" };

export function SourcesPage() {
  const [sources, setSources] = useState<Source[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [showInactive, setShowInactive] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [editForm, setEditForm] = useState({ name: "", description: "", authenticationConfig: "" });
  const [fieldsOpenKey, setFieldsOpenKey] = useState<string | null>(null);
  const loggedIn = isLoggedIn();
  const { notify } = useToast();

  function reload() {
    get<Source[]>("/api/sources")
      .then(setSources)
      .catch((err) => setError((err as Error).message))
      .finally(() => setLoading(false));
  }

  useEffect(reload, []);

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await post("/api/sources", form);
      setForm(emptyForm);
      setShowCreate(false);
      reload();
      notify(`Source "${form.key}" created.`);
    } catch (err) {
      setError((err as Error).message);
      notify((err as Error).message, "error");
    }
  }

  function startEdit(source: Source) {
    setEditingKey(editingKey === source.key ? null : source.key);
    setEditForm({ name: source.name, description: source.description, authenticationConfig: "" });
  }

  async function saveEdit(key: string) {
    setError(null);
    try {
      await put(`/api/sources/${key}`, editForm);
      setEditingKey(null);
      reload();
      notify(`Source "${key}" updated.`);
    } catch (err) {
      setError((err as Error).message);
      notify((err as Error).message, "error");
    }
  }

  async function deactivate(key: string) {
    if (!confirm(`Deactivate Source "${key}"? Existing Deliveries/history are kept.`)) return;
    setError(null);
    try {
      await del(`/api/sources/${key}`);
      reload();
      notify(`Source "${key}" deactivated.`);
    } catch (err) {
      setError((err as Error).message);
      notify((err as Error).message, "error");
    }
  }

  async function hardDelete(key: string) {
    if (!confirm(`Permanently delete Source "${key}"? This cannot be undone. Blocked if any Source Event still exists for it.`))
      return;
    setError(null);
    try {
      await del(`/api/sources/${key}?hard=true`);
      reload();
      notify(`Source "${key}" permanently deleted.`);
    } catch (err) {
      setError((err as Error).message);
      notify((err as Error).message, "error");
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1>Sources</h1>
        {loggedIn && (
          <button className="btn-primary" onClick={() => setShowCreate(!showCreate)}>
            {showCreate ? "Cancel" : "+ New Source"}
          </button>
        )}
      </div>
      {error && <p className="error">{error}</p>}

      <label className="inline-checkbox">
        <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
        Show deactivated (soft-deleted) Sources too
      </label>

      {showCreate && loggedIn && (
        <form onSubmit={handleCreate} className="card form-card">
          <div className="form-grid">
            <label>
              Key
              <input required value={form.key} onChange={(e) => setForm({ ...form, key: e.target.value })} />
            </label>
            <label>
              Name
              <input required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </label>
            <label className="form-wide">
              Description
              <input
                required
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
              />
            </label>
            <label>
              Authentication config (optional)
              <input
                value={form.authenticationConfig}
                onChange={(e) => setForm({ ...form, authenticationConfig: e.target.value })}
              />
            </label>
          </div>
          <button type="submit" className="btn-primary">
            Create Source
          </button>
        </form>
      )}

      <div className="card-list">
        {sources
          .filter((s) => showInactive || s.status === "ACTIVE")
          .map((s) => (
          <div className="card entity-card" key={s.key}>
            <div className="entity-card-header">
              <div>
                <strong>{s.name}</strong>
                <div className="muted">
                  <code>{s.key}</code> &middot; {s.description}
                </div>
              </div>
              <div className="entity-card-actions">
                <StatusBadge value={s.status} />
                <button onClick={() => setFieldsOpenKey(fieldsOpenKey === s.key ? null : s.key)}>
                  {fieldsOpenKey === s.key ? "Hide field mapping" : "Field mapping"}
                </button>
                {loggedIn && (
                  <>
                    <button onClick={() => startEdit(s)}>{editingKey === s.key ? "Close" : "Edit"}</button>
                    {s.status === "ACTIVE" && <button onClick={() => deactivate(s.key)}>Deactivate</button>}
                    <button className="btn-danger" onClick={() => hardDelete(s.key)}>
                      Delete permanently
                    </button>
                  </>
                )}
              </div>
            </div>
            {fieldsOpenKey === s.key && (
              <div className="entity-card-edit">
                <p className="muted">
                  Fields each Source Event can supply — registered here so Subscriptions can map them via dropdown
                  instead of free-typed JSONPath.
                </p>
                <SourceEventFields sourceKey={s.key} loggedIn={loggedIn} />
              </div>
            )}
            {editingKey === s.key && (
              <div className="entity-card-edit">
                <div className="form-grid">
                  <label>
                    Name
                    <input value={editForm.name} onChange={(e) => setEditForm({ ...editForm, name: e.target.value })} />
                  </label>
                  <label className="form-wide">
                    Description
                    <input
                      value={editForm.description}
                      onChange={(e) => setEditForm({ ...editForm, description: e.target.value })}
                    />
                  </label>
                </div>
                <button className="btn-primary" onClick={() => saveEdit(s.key)}>
                  Save
                </button>
              </div>
            )}
          </div>
        ))}
        {loading && <p className="muted">Loading Sources...</p>}
        {!loading && sources.filter((s) => showInactive || s.status === "ACTIVE").length === 0 && (
          <p className="muted">{sources.length === 0 ? "No Sources yet." : "No active Sources — try “Show deactivated”."}</p>
        )}
      </div>
    </div>
  );
}
