import { FormEvent, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { get, post } from "../api";
import { isLoggedIn } from "../auth";
import { Source, SourceEvent, Subscription, Target, TargetEndpoint } from "../types";
import { StatusBadge } from "../components/StatusBadge";
import { MappingBuilder } from "../components/MappingBuilder";
import { SearchBox } from "../components/SearchBox";
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

/**
 * List of Subscriptions — Source Event -> Target Endpoint -> Status at a glance, browsing and
 * creation only. Editing (General/Source/Target/Mapping/Filter/Delivery Policy) and the dangerous
 * actions all live on SubscriptionDetailPage now (Stage 3, maintainer request 2026-09-30).
 */
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
  const [search, setSearch] = useState("");
  const [form, setForm] = useState(emptyForm);
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

  const query = search.trim().toLowerCase();
  const visible = subscriptions
    .filter((s) => showInactive || s.status === "ACTIVE")
    .filter(
      (s) =>
        !query ||
        s.name.toLowerCase().includes(query) ||
        s.sourceKey.toLowerCase().includes(query) ||
        s.sourceEventKey.toLowerCase().includes(query) ||
        s.targetKey.toLowerCase().includes(query) ||
        s.targetEndpointKey.toLowerCase().includes(query)
    );

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
      <div className="list-controls">
        <label className="inline-checkbox">
          <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
          Show deactivated (soft-deleted) Subscriptions too
        </label>
        <SearchBox value={search} onChange={setSearch} placeholder="Search by name, source, target..." />
      </div>

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
          </div>
          <p className="muted" style={{ margin: 0 }}>
            Filter and Delivery Policy can be configured after creation, from the Subscription's own page.
          </p>
          {error && <p className="error">{error}</p>}
          <button type="submit" className="btn-primary">
            Create Subscription
          </button>
        </form>
      )}

      <div className="card-list">
        {visible.map((s) => (
          <Link className="entity-row" to={`/subscriptions/${s.id}`} key={s.id}>
            <div className="entity-row-main">
              <div className="subscription-flow subscription-flow-primary">
                <span className="flow-group">
                  <strong>{s.sourceKey}</strong>
                  <span className="flow-chevron">&rsaquo;</span>
                  <code>{s.sourceEventKey}</code>
                </span>
                <span className="flow-arrow">&rarr;</span>
                <span className="flow-group">
                  <strong>{s.targetKey}</strong>
                  <span className="flow-chevron">&rsaquo;</span>
                  <code>{s.targetEndpointKey}</code>
                </span>
              </div>
              <div className="entity-row-sub">{s.name}</div>
            </div>
            <div className="entity-card-actions">
              {s.mappingWarnings.length > 0 && (
                <span className="badge badge-warn" title={s.mappingWarnings.join("\n")}>
                  {s.mappingWarnings.length} mapping warning{s.mappingWarnings.length > 1 ? "s" : ""}
                </span>
              )}
              <StatusBadge value={s.status} />
            </div>
          </Link>
        ))}
        {loading && <p className="muted">Loading Subscriptions...</p>}
        {!loading && visible.length === 0 && (
          <p className="muted">
            {subscriptions.length === 0
              ? "No Subscriptions yet."
              : query
                ? `No Subscriptions match "${search}".`
                : "No active Subscriptions — try “Show deactivated”."}
          </p>
        )}
      </div>
    </div>
  );
}
