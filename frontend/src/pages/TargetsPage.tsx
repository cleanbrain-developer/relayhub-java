import { FormEvent, useEffect, useState } from "react";
import { del, get, post, put } from "../api";
import { isLoggedIn } from "../auth";
import { Target } from "../types";
import { StatusBadge } from "../components/StatusBadge";

const emptyForm = { key: "", name: "", description: "", baseUrl: "", authenticationConfig: "" };

export function TargetsPage() {
  const [targets, setTargets] = useState<Target[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [showInactive, setShowInactive] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [editForm, setEditForm] = useState({ name: "", description: "", baseUrl: "", authenticationConfig: "" });
  const loggedIn = isLoggedIn();

  function reload() {
    get<Target[]>("/api/targets").then(setTargets).catch((err) => setError((err as Error).message));
  }

  useEffect(reload, []);

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await post("/api/targets", form);
      setForm(emptyForm);
      setShowCreate(false);
      reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  function startEdit(target: Target) {
    setEditingKey(editingKey === target.key ? null : target.key);
    setEditForm({ name: target.name, description: target.description, baseUrl: target.baseUrl, authenticationConfig: "" });
  }

  async function saveEdit(key: string) {
    setError(null);
    try {
      await put(`/api/targets/${key}`, editForm);
      setEditingKey(null);
      reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  async function deactivate(key: string) {
    if (!confirm(`Deactivate Target "${key}"? Existing Deliveries/history are kept.`)) return;
    setError(null);
    try {
      await del(`/api/targets/${key}`);
      reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1>Targets</h1>
        {loggedIn && (
          <button className="btn-primary" onClick={() => setShowCreate(!showCreate)}>
            {showCreate ? "Cancel" : "+ New Target"}
          </button>
        )}
      </div>
      {error && <p className="error">{error}</p>}

      <label className="inline-checkbox">
        <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
        Show deactivated (soft-deleted) Targets too
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
            <label className="form-wide">
              Base URL
              <input
                required
                value={form.baseUrl}
                onChange={(e) => setForm({ ...form, baseUrl: e.target.value })}
                placeholder="https://example.com"
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
            Create Target
          </button>
        </form>
      )}

      <div className="card-list">
        {targets
          .filter((t) => showInactive || t.status === "ACTIVE")
          .map((t) => (
          <div className="card entity-card" key={t.key}>
            <div className="entity-card-header">
              <div>
                <strong>{t.name}</strong>
                <div className="muted">
                  <code>{t.key}</code> &middot; <code>{t.baseUrl}</code>
                </div>
              </div>
              <div className="entity-card-actions">
                <StatusBadge value={t.status} />
                {loggedIn && (
                  <>
                    <button onClick={() => startEdit(t)}>{editingKey === t.key ? "Close" : "Edit"}</button>
                    {t.status === "ACTIVE" && <button onClick={() => deactivate(t.key)}>Deactivate</button>}
                  </>
                )}
              </div>
            </div>
            {editingKey === t.key && (
              <div className="entity-card-edit">
                <div className="form-grid">
                  <label>
                    Name
                    <input value={editForm.name} onChange={(e) => setEditForm({ ...editForm, name: e.target.value })} />
                  </label>
                  <label className="form-wide">
                    Base URL
                    <input
                      value={editForm.baseUrl}
                      onChange={(e) => setEditForm({ ...editForm, baseUrl: e.target.value })}
                    />
                  </label>
                </div>
                <button className="btn-primary" onClick={() => saveEdit(t.key)}>
                  Save
                </button>
              </div>
            )}
          </div>
        ))}
        {targets.filter((t) => showInactive || t.status === "ACTIVE").length === 0 && (
          <p className="muted">{targets.length === 0 ? "No Targets yet." : "No active Targets — try “Show deactivated”."}</p>
        )}
      </div>
    </div>
  );
}
