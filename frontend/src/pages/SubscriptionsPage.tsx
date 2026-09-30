import { FormEvent, useEffect, useState } from "react";
import { del, get, post, put } from "../api";
import { isLoggedIn } from "../auth";
import { Source, SourceEvent, Subscription, Target, TargetEndpoint } from "../types";
import { StatusBadge } from "../components/StatusBadge";
import { MappingBuilder } from "../components/MappingBuilder";
import { useToast } from "../toast";

const emptyForm = {
  sourceKey: "",
  sourceEventKey: "",
  targetKey: "",
  targetEndpointKey: "",
  name: "",
  description: "",
  targetPayloadTemplate: "",
  filterExpression: "",
  maxAttempts: "",
};

export function SubscriptionsPage() {
  const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
  const [loading, setLoading] = useState(true);
  const [sources, setSources] = useState<Source[]>([]);
  const [targets, setTargets] = useState<Target[]>([]);
  const [events, setEvents] = useState<SourceEvent[]>([]);
  const [targetEndpoints, setTargetEndpoints] = useState<TargetEndpoint[]>([]);
  const [dropdownError, setDropdownError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [showInactive, setShowInactive] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState({
    name: "",
    description: "",
    targetPayloadTemplate: "",
    filterExpression: "",
    maxAttempts: "",
  });
  const loggedIn = isLoggedIn();
  const { notify } = useToast();

  function reload() {
    get<Subscription[]>("/api/subscriptions")
      .then(setSubscriptions)
      .catch((err) => setError((err as Error).message))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    reload();
    get<Source[]>("/api/sources")
      .then(setSources)
      .catch(() => setDropdownError("Couldn't load Sources for the create form — try reloading the page."));
    get<Target[]>("/api/targets")
      .then(setTargets)
      .catch(() => setDropdownError("Couldn't load Targets for the create form — try reloading the page."));
  }, []);

  useEffect(() => {
    if (!form.sourceKey) {
      setEvents([]);
      return;
    }
    get<SourceEvent[]>(`/api/sources/${form.sourceKey}/events`)
      .then(setEvents)
      .catch(() => {
        setEvents([]);
        setDropdownError("Couldn't load Source Events for the selected Source — try reloading the page.");
      });
  }, [form.sourceKey]);

  useEffect(() => {
    if (!form.targetKey) {
      setTargetEndpoints([]);
      return;
    }
    get<TargetEndpoint[]>(`/api/targets/${form.targetKey}/endpoints`)
      .then(setTargetEndpoints)
      .catch(() => {
        setTargetEndpoints([]);
        setDropdownError("Couldn't load Target Endpoints for the selected Target — try reloading the page.");
      });
  }, [form.targetKey]);

  function toCreateBody(state: typeof form) {
    const { sourceKey, sourceEventKey, targetKey, targetEndpointKey, ...rest } = state;
    return {
      sourceKey,
      sourceEventKey,
      targetKey,
      targetEndpointKey,
      ...rest,
      filterExpression: rest.filterExpression || null,
      maxAttempts: rest.maxAttempts ? Number(rest.maxAttempts) : null,
    };
  }

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await post("/api/subscriptions", toCreateBody(form));
      setForm(emptyForm);
      setShowCreate(false);
      reload();
      notify(`Subscription "${form.name}" created.`);
    } catch (err) {
      setError((err as Error).message);
      notify((err as Error).message, "error");
    }
  }

  function startEdit(sub: Subscription) {
    setEditingId(editingId === sub.id ? null : sub.id);
    setEditForm({
      name: sub.name,
      description: sub.description,
      targetPayloadTemplate: sub.targetPayloadTemplate,
      filterExpression: sub.filterExpression ?? "",
      maxAttempts: sub.maxAttempts !== null ? String(sub.maxAttempts) : "",
    });
  }

  async function saveEdit(id: string) {
    setError(null);
    try {
      await put(`/api/subscriptions/${id}`, {
        ...editForm,
        filterExpression: editForm.filterExpression || null,
        maxAttempts: editForm.maxAttempts ? Number(editForm.maxAttempts) : null,
      });
      setEditingId(null);
      reload();
      notify("Subscription updated.");
    } catch (err) {
      setError((err as Error).message);
      notify((err as Error).message, "error");
    }
  }

  async function deactivate(id: string) {
    if (!confirm("Deactivate this Subscription? Existing Delivery history is kept.")) return;
    setError(null);
    try {
      await del(`/api/subscriptions/${id}`);
      reload();
      notify("Subscription deactivated.");
    } catch (err) {
      setError((err as Error).message);
      notify((err as Error).message, "error");
    }
  }

  async function hardDelete(id: string) {
    if (!confirm("Permanently delete this Subscription? This cannot be undone.")) return;
    setError(null);
    try {
      await del(`/api/subscriptions/${id}?hard=true`);
      reload();
      notify("Subscription permanently deleted.");
    } catch (err) {
      setError((err as Error).message);
      notify((err as Error).message, "error");
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

      <label className="inline-checkbox">
        <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
        Show deactivated (soft-deleted) Subscriptions too
      </label>

      {showCreate && loggedIn && (
        <form onSubmit={handleCreate} className="card form-card">
          {dropdownError && <p className="error">{dropdownError}</p>}
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
              <select
                required
                value={form.targetKey}
                onChange={(e) => setForm({ ...form, targetKey: e.target.value, targetEndpointKey: "" })}
              >
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
              Target Endpoint
              <select
                required
                value={form.targetEndpointKey}
                onChange={(e) => setForm({ ...form, targetEndpointKey: e.target.value })}
                disabled={!form.targetKey}
              >
                <option value="" disabled>
                  Select an Endpoint
                </option>
                {targetEndpoints.map((ep) => (
                  <option key={ep.key} value={ep.key}>
                    {ep.key} ({ep.httpMethod} {ep.path})
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
            <label className="form-wide">
              Payload mapping
              <MappingBuilder
                value={form.targetPayloadTemplate}
                onChange={(t) => setForm({ ...form, targetPayloadTemplate: t })}
                sourceKey={form.sourceKey}
                sourceEventKey={form.sourceEventKey}
                targetKey={form.targetKey}
                targetEndpointKey={form.targetEndpointKey}
              />
            </label>
            <label className="form-wide">
              Filter (optional, free text — not yet enforced, see ADR-0005)
              <input
                placeholder='e.g. status == "DELAYED"'
                value={form.filterExpression}
                onChange={(e) => setForm({ ...form, filterExpression: e.target.value })}
              />
            </label>
            <label>
              Max attempts override (optional)
              <input
                type="number"
                min={1}
                max={10}
                placeholder="uses the global default"
                value={form.maxAttempts}
                onChange={(e) => setForm({ ...form, maxAttempts: e.target.value })}
              />
            </label>
          </div>
          <button type="submit" className="btn-primary">
            Create Subscription
          </button>
        </form>
      )}

      <div className="card-list">
        {subscriptions
          .filter((s) => showInactive || s.status === "ACTIVE")
          .map((s) => (
          <div className="card entity-card" key={s.id}>
            <div className="entity-card-header">
              <div>
                <strong>{s.name}</strong>
                <div className="muted">
                  {s.sourceEventKey} &rarr; {s.targetKey}/{s.targetEndpointKey} &middot; {s.targetMethod}{" "}
                  <code>{s.targetPath}</code>
                </div>
              </div>
              <div className="entity-card-actions">
                <StatusBadge value={s.status} />
                {loggedIn && (
                  <>
                    <button onClick={() => startEdit(s)}>{editingId === s.id ? "Close" : "Edit"}</button>
                    {s.status === "ACTIVE" && <button onClick={() => deactivate(s.id)}>Deactivate</button>}
                    <button className="btn-danger" onClick={() => hardDelete(s.id)}>
                      Delete permanently
                    </button>
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
                  <label className="form-wide">
                    Payload mapping
                    <MappingBuilder
                      value={editForm.targetPayloadTemplate}
                      onChange={(t) => setEditForm({ ...editForm, targetPayloadTemplate: t })}
                      sourceKey={s.sourceKey}
                      sourceEventKey={s.sourceEventKey}
                      targetKey={s.targetKey}
                      targetEndpointKey={s.targetEndpointKey}
                    />
                  </label>
                  <label className="form-wide">
                    Filter (optional, free text — not yet enforced, see ADR-0005)
                    <input
                      value={editForm.filterExpression}
                      onChange={(e) => setEditForm({ ...editForm, filterExpression: e.target.value })}
                    />
                  </label>
                  <label>
                    Max attempts override (optional — currently effective: {s.effectiveMaxAttempts})
                    <input
                      type="number"
                      min={1}
                      max={10}
                      placeholder="uses the global default"
                      value={editForm.maxAttempts}
                      onChange={(e) => setEditForm({ ...editForm, maxAttempts: e.target.value })}
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
        {loading && <p className="muted">Loading Subscriptions...</p>}
        {!loading && subscriptions.filter((s) => showInactive || s.status === "ACTIVE").length === 0 && (
          <p className="muted">
            {subscriptions.length === 0 ? "No Subscriptions yet." : "No active Subscriptions — try “Show deactivated”."}
          </p>
        )}
      </div>
    </div>
  );
}
