import { FormEvent, useEffect, useState } from "react";
import { del, get, post, put } from "../api";
import { isLoggedIn } from "../auth";
import { HttpVerb, Source, SourceEvent, Subscription, Target } from "../types";
import { StatusBadge } from "../components/StatusBadge";
import { MappingBuilder } from "../components/MappingBuilder";

const HTTP_VERBS: HttpVerb[] = ["GET", "POST", "PUT", "PATCH", "DELETE"];

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
  const [showCreate, setShowCreate] = useState(false);
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
      setShowCreate(false);
      reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  function startEdit(sub: Subscription) {
    setEditingId(editingId === sub.id ? null : sub.id);
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
      <div className="page-header">
        <h1>Subscriptions</h1>
        {loggedIn && (
          <button className="btn-primary" onClick={() => setShowCreate(!showCreate)}>
            {showCreate ? "Cancel" : "+ New Subscription"}
          </button>
        )}
      </div>
      {error && <p className="error">{error}</p>}

      {showCreate && loggedIn && (
        <form onSubmit={handleCreate} className="card form-card">
          <div className="form-grid">
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
            <label className="form-wide">
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
                {HTTP_VERBS.map((m) => (
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
              Payload mapping
              <MappingBuilder
                value={form.targetPayloadTemplate}
                onChange={(t) => setForm({ ...form, targetPayloadTemplate: t })}
              />
            </label>
            <label>
              Retry policy (optional, free text)
              <input value={form.retryPolicy} onChange={(e) => setForm({ ...form, retryPolicy: e.target.value })} />
            </label>
          </div>
          <button type="submit" className="btn-primary">
            Create Subscription
          </button>
        </form>
      )}

      <div className="card-list">
        {subscriptions.map((s) => (
          <div className="card entity-card" key={s.id}>
            <div className="entity-card-header">
              <div>
                <strong>{s.name}</strong>
                <div className="muted">
                  {s.sourceEventKey} &rarr; {s.targetKey} &middot; {s.targetMethod} <code>{s.targetPath}</code>
                </div>
              </div>
              <div className="entity-card-actions">
                <StatusBadge value={s.status} />
                {loggedIn && (
                  <>
                    <button onClick={() => startEdit(s)}>{editingId === s.id ? "Close" : "Edit"}</button>
                    {s.status === "ACTIVE" && <button onClick={() => deactivate(s.id)}>Deactivate</button>}
                  </>
                )}
              </div>
            </div>
            {editingId === s.id && (
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
                  <label>
                    Target method
                    <select
                      value={editForm.targetMethod}
                      onChange={(e) => setEditForm({ ...editForm, targetMethod: e.target.value as HttpVerb })}
                    >
                      {HTTP_VERBS.map((m) => (
                        <option key={m}>{m}</option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Target path
                    <input
                      value={editForm.targetPath}
                      onChange={(e) => setEditForm({ ...editForm, targetPath: e.target.value })}
                    />
                  </label>
                  <label className="form-wide">
                    Payload mapping
                    <MappingBuilder
                      value={editForm.targetPayloadTemplate}
                      onChange={(t) => setEditForm({ ...editForm, targetPayloadTemplate: t })}
                    />
                  </label>
                  <label>
                    Retry policy
                    <input
                      value={editForm.retryPolicy}
                      onChange={(e) => setEditForm({ ...editForm, retryPolicy: e.target.value })}
                    />
                  </label>
                </div>
                <button className="btn-primary" onClick={() => saveEdit(s.id)}>
                  Save
                </button>
              </div>
            )}
          </div>
        ))}
        {subscriptions.length === 0 && <p className="muted">No Subscriptions yet.</p>}
      </div>
    </div>
  );
}
