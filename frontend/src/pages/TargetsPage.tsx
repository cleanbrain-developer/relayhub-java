import { FormEvent, useEffect, useState } from "react";
import { del, get, post, put } from "../api";
import { isLoggedIn } from "../auth";
import { Target } from "../types";
import { StatusBadge } from "../components/StatusBadge";

const emptyForm = { key: "", name: "", description: "", baseUrl: "", authenticationConfig: "" };

export function TargetsPage() {
  const [targets, setTargets] = useState<Target[]>([]);
  const [error, setError] = useState<string | null>(null);
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
      reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  function startEdit(target: Target) {
    setEditingKey(target.key);
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
      <h1>Targets</h1>
      {error && <p className="error">{error}</p>}

      <table>
        <thead>
          <tr>
            <th>Key</th>
            <th>Name</th>
            <th>Base URL</th>
            <th>Status</th>
            {loggedIn && <th></th>}
          </tr>
        </thead>
        <tbody>
          {targets.map((t) =>
            editingKey === t.key ? (
              <tr key={t.key}>
                <td>{t.key}</td>
                <td>
                  <input value={editForm.name} onChange={(e) => setEditForm({ ...editForm, name: e.target.value })} />
                </td>
                <td>
                  <input
                    value={editForm.baseUrl}
                    onChange={(e) => setEditForm({ ...editForm, baseUrl: e.target.value })}
                  />
                </td>
                <td>
                  <StatusBadge value={t.status} />
                </td>
                <td>
                  <button onClick={() => saveEdit(t.key)}>Save</button>
                  <button onClick={() => setEditingKey(null)}>Cancel</button>
                </td>
              </tr>
            ) : (
              <tr key={t.key}>
                <td>{t.key}</td>
                <td>{t.name}</td>
                <td>
                  <code>{t.baseUrl}</code>
                </td>
                <td>
                  <StatusBadge value={t.status} />
                </td>
                {loggedIn && (
                  <td>
                    <button onClick={() => startEdit(t)}>Edit</button>
                    {t.status === "ACTIVE" && <button onClick={() => deactivate(t.key)}>Deactivate</button>}
                  </td>
                )}
              </tr>
            )
          )}
        </tbody>
      </table>

      {loggedIn && (
        <>
          <h2>Register a Target</h2>
          <form onSubmit={handleCreate} className="form-grid">
            <label>
              Key
              <input required value={form.key} onChange={(e) => setForm({ ...form, key: e.target.value })} />
            </label>
            <label>
              Name
              <input required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </label>
            <label>
              Description
              <input
                required
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
              />
            </label>
            <label>
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
            <button type="submit">Create</button>
          </form>
        </>
      )}
    </div>
  );
}
