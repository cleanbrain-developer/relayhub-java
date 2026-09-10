import { FormEvent, useEffect, useState } from "react";
import { del, get, post, put } from "../api";
import { isLoggedIn } from "../auth";
import { HttpVerb, Source, SourceEvent, Subscription, Target } from "../types";
import { StatusBadge } from "../components/StatusBadge";

const emptyForm = {
  sourceKey: "",
  sourceEventKey: "",
  targetKey: "",
  name: "",
  description: "",
  targetMethod: "POST" as HttpVerb,
  targetPath: "",
  targetPayloadTemplate: "",
  retryPolicy: "",
};

export function SubscriptionsPage() {
  const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
  const [sources, setSources] = useState<Source[]>([]);
  const [targets, setTargets] = useState<Target[]>([]);
  const [events, setEvents] = useState<SourceEvent[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState({
    name: "",
    description: "",
    targetMethod: "POST" as HttpVerb,
    targetPath: "",
    targetPayloadTemplate: "",
    retryPolicy: "",
  });
  const loggedIn = isLoggedIn();

  function reload() {
    get<Subscription[]>("/api/subscriptions").then(setSubscriptions).catch((err) => setError((err as Error).message));
  }

  useEffect(() => {
    reload();
    get<Source[]>("/api/sources").then(setSources).catch(() => {});
    get<Target[]>("/api/targets").then(setTargets).catch(() => {});
  }, []);

  useEffect(() => {
    if (!form.sourceKey) {
      setEvents([]);
      return;
    }
    get<SourceEvent[]>(`/api/sources/${form.sourceKey}/events`).then(setEvents).catch(() => setEvents([]));
  }, [form.sourceKey]);

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const { sourceKey, sourceEventKey, targetKey, ...rest } = form;
      await post("/api/subscriptions", { sourceKey, sourceEventKey, targetKey, ...rest });
      setForm(emptyForm);
      reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  function startEdit(sub: Subscription) {
    setEditingId(sub.id);
    setEditForm({
      name: sub.name,
      description: sub.description,
      targetMethod: sub.targetMethod,
      targetPath: sub.targetPath,
      targetPayloadTemplate: sub.targetPayloadTemplate,
      retryPolicy: sub.retryPolicy ?? "",
    });
  }

  async function saveEdit(id: string) {
    setError(null);
    try {
      await put(`/api/subscriptions/${id}`, editForm);
      setEditingId(null);
      reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  async function deactivate(id: string) {
    if (!confirm("Deactivate this Subscription? Existing Delivery history is kept.")) return;
    setError(null);
    try {
      await del(`/api/subscriptions/${id}`);
      reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  return (
    <div>
      <h1>Subscriptions</h1>
      {error && <p className="error">{error}</p>}

      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Source Event</th>
            <th>Target</th>
            <th>Method / Path</th>
            <th>Status</th>
            {loggedIn && <th></th>}
          </tr>
        </thead>
        <tbody>
          {subscriptions.map((s) =>
            editingId === s.id ? (
              <tr key={s.id}>
                <td>
                  <input value={editForm.name} onChange={(e) => setEditForm({ ...editForm, name: e.target.value })} />
                </td>
                <td>{s.sourceEventKey}</td>
                <td>{s.targetKey}</td>
                <td>
                  <select
                    value={editForm.targetMethod}
                    onChange={(e) => setEditForm({ ...editForm, targetMethod: e.target.value as HttpVerb })}
                  >
                    {["GET", "POST", "PUT", "PATCH", "DELETE"].map((m) => (
                      <option key={m}>{m}</option>
                    ))}
                  </select>
                  <input
                    value={editForm.targetPath}
                    onChange={(e) => setEditForm({ ...editForm, targetPath: e.target.value })}
                  />
                </td>
                <td>
                  <StatusBadge value={s.status} />
                </td>
                <td>
                  <button onClick={() => saveEdit(s.id)}>Save</button>
                  <button onClick={() => setEditingId(null)}>Cancel</button>
                </td>
              </tr>
            ) : (
              <tr key={s.id}>
                <td>{s.name}</td>
                <td>{s.sourceEventKey}</td>
                <td>{s.targetKey}</td>
                <td>
                  {s.targetMethod} <code>{s.targetPath}</code>
                </td>
                <td>
                  <StatusBadge value={s.status} />
                </td>
                {loggedIn && (
                  <td>
                    <button onClick={() => startEdit(s)}>Edit</button>
                    {s.status === "ACTIVE" && <button onClick={() => deactivate(s.id)}>Deactivate</button>}
                  </td>
                )}
              </tr>
            )
          )}
        </tbody>
      </table>

      {loggedIn && (
        <>
          <h2>Register a Subscription</h2>
          <form onSubmit={handleCreate} className="form-grid">
            <label>
              Source
              <select
                required
                value={form.sourceKey}
                onChange={(e) => setForm({ ...form, sourceKey: e.target.value, sourceEventKey: "" })}
              >
                <option value="" disabled>
                  Select a Source
                </option>
                {sources.map((s) => (
                  <option key={s.key} value={s.key}>
                    {s.key}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Source Event
              <select
                required
                value={form.sourceEventKey}
                onChange={(e) => setForm({ ...form, sourceEventKey: e.target.value })}
                disabled={!form.sourceKey}
              >
                <option value="" disabled>
                  Select an Event
                </option>
                {events.map((ev) => (
                  <option key={ev.key} value={ev.key}>
                    {ev.key} ({ev.operation})
                  </option>
                ))}
              </select>
            </label>
            <label>
              Target
              <select required value={form.targetKey} onChange={(e) => setForm({ ...form, targetKey: e.target.value })}>
                <option value="" disabled>
                  Select a Target
                </option>
                {targets.map((t) => (
                  <option key={t.key} value={t.key}>
                    {t.key}
                  </option>
                ))}
              </select>
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
              Target method
              <select
                value={form.targetMethod}
                onChange={(e) => setForm({ ...form, targetMethod: e.target.value as HttpVerb })}
              >
                {["GET", "POST", "PUT", "PATCH", "DELETE"].map((m) => (
                  <option key={m}>{m}</option>
                ))}
              </select>
            </label>
            <label>
              Target path
              <input
                required
                value={form.targetPath}
                onChange={(e) => setForm({ ...form, targetPath: e.target.value })}
                placeholder="/webhook"
              />
            </label>
            <label className="form-wide">
              Target payload template (JSON, ${"$"}{"{"}$.jsonpath{"}"} placeholders)
              <textarea
                required
                value={form.targetPayloadTemplate}
                onChange={(e) => setForm({ ...form, targetPayloadTemplate: e.target.value })}
                rows={3}
              />
            </label>
            <label>
              Retry policy (optional, free text)
              <input
                value={form.retryPolicy}
                onChange={(e) => setForm({ ...form, retryPolicy: e.target.value })}
              />
            </label>
            <button type="submit">Create</button>
          </form>
        </>
      )}
    </div>
  );
}
