import { FormEvent, useEffect, useState } from "react";
import { del, get, post, put } from "../api";
import { isLoggedIn } from "../auth";
import { Source } from "../types";
import { StatusBadge } from "../components/StatusBadge";

const emptyForm = { key: "", name: "", description: "", authenticationConfig: "" };

export function SourcesPage() {
  const [sources, setSources] = useState<Source[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [editForm, setEditForm] = useState({ name: "", description: "", authenticationConfig: "" });
  const loggedIn = isLoggedIn();

  function reload() {
    get<Source[]>("/api/sources").then(setSources).catch((err) => setError((err as Error).message));
  }

  useEffect(reload, []);

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await post("/api/sources", form);
      setForm(emptyForm);
      reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  function startEdit(source: Source) {
    setEditingKey(source.key);
    setEditForm({ name: source.name, description: source.description, authenticationConfig: "" });
  }

  async function saveEdit(key: string) {
    setError(null);
    try {
      await put(`/api/sources/${key}`, editForm);
      setEditingKey(null);
      reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  async function deactivate(key: string) {
    if (!confirm(`Deactivate Source "${key}"? Existing Deliveries/history are kept.`)) return;
    setError(null);
    try {
      await del(`/api/sources/${key}`);
      reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  return (
    <div>
      <h1>Sources</h1>
      {error && <p className="error">{error}</p>}

      <table>
        <thead>
          <tr>
            <th>Key</th>
            <th>Name</th>
            <th>Description</th>
            <th>Status</th>
            {loggedIn && <th></th>}
          </tr>
        </thead>
        <tbody>
          {sources.map((s) =>
            editingKey === s.key ? (
              <tr key={s.key}>
                <td>{s.key}</td>
                <td>
                  <input value={editForm.name} onChange={(e) => setEditForm({ ...editForm, name: e.target.value })} />
                </td>
                <td>
                  <input
                    value={editForm.description}
                    onChange={(e) => setEditForm({ ...editForm, description: e.target.value })}
                  />
                </td>
                <td>
                  <StatusBadge value={s.status} />
                </td>
                <td>
                  <button onClick={() => saveEdit(s.key)}>Save</button>
                  <button onClick={() => setEditingKey(null)}>Cancel</button>
                </td>
              </tr>
            ) : (
              <tr key={s.key}>
                <td>{s.key}</td>
                <td>{s.name}</td>
                <td>{s.description}</td>
                <td>
                  <StatusBadge value={s.status} />
                </td>
                {loggedIn && (
                  <td>
                    <button onClick={() => startEdit(s)}>Edit</button>
                    {s.status === "ACTIVE" && <button onClick={() => deactivate(s.key)}>Deactivate</button>}
                  </td>
                )}
              </tr>
            )
          )}
        </tbody>
      </table>

      {loggedIn && (
        <>
          <h2>Register a Source</h2>
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
