import { Fragment, FormEvent, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { get, getAuthed, put, post } from "../api";
import { isLoggedIn } from "../auth";
import { CanonicalEvent, Delivery, DeliveryAttempt, DeliverySettings, DeliveryState, Target } from "../types";
import { StatusBadge } from "../components/StatusBadge";
import { AttemptDetail } from "../components/AttemptDetail";
import { EventDetail } from "../components/EventDetail";
import { useToast } from "../toast";

const STATES: (DeliveryState | "ALL")[] = ["ALL", "PENDING", "SUCCEEDED", "DEAD"];

export function DeliveriesPage() {
  const [params] = useSearchParams();
  const highlight = params.get("highlight");
  const [stateFilter, setStateFilter] = useState<DeliveryState | "ALL">("ALL");
  const [deliveries, setDeliveries] = useState<Delivery[]>([]);
  const [loading, setLoading] = useState(true);
  const [targets, setTargets] = useState<Target[]>([]);
  const [expandedId, setExpandedId] = useState<string | null>(highlight);
  const [attempts, setAttempts] = useState<DeliveryAttempt[]>([]);
  const [detailAttemptId, setDetailAttemptId] = useState<string | null>(null);
  const [eventOpenId, setEventOpenId] = useState<string | null>(null);
  const [event, setEvent] = useState<CanonicalEvent | null>(null);
  const [eventError, setEventError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [maxAttempts, setMaxAttempts] = useState<number | null>(null);
  const [editingPolicy, setEditingPolicy] = useState(false);
  const [policyForm, setPolicyForm] = useState("");
  const [policyError, setPolicyError] = useState<string | null>(null);
  const loggedIn = isLoggedIn();
  const { notify } = useToast();

  const targetKeyById = useMemo(() => Object.fromEntries(targets.map((t) => [t.id, t.key])), [targets]);

  // Reacts to the ?highlight= param changing while already mounted on this route (react-router
  // reuses the component instance across search-param-only navigations, so the useState initial
  // value above only covers the first mount).
  useEffect(() => {
    if (highlight) setExpandedId(highlight);
  }, [highlight]);

  function reload() {
    setLoading(true);
    const query = stateFilter === "ALL" ? "" : `?state=${stateFilter}`;
    get<Delivery[]>(`/api/deliveries${query}`)
      .then(setDeliveries)
      .catch((err) => setError((err as Error).message))
      .finally(() => setLoading(false));
  }

  useEffect(reload, [stateFilter]);
  useEffect(() => {
    get<Target[]>("/api/targets").then(setTargets).catch(() => {});
  }, []);

  function loadPolicy() {
    get<DeliverySettings>("/api/delivery-settings")
      .then((s) => setMaxAttempts(s.maxAttempts))
      .catch(() => {});
  }

  useEffect(loadPolicy, []);

  async function savePolicy(e: FormEvent) {
    e.preventDefault();
    setPolicyError(null);
    const parsed = Number(policyForm);
    try {
      const updated = await put<DeliverySettings>("/api/delivery-settings", { maxAttempts: parsed });
      setMaxAttempts(updated.maxAttempts);
      setEditingPolicy(false);
      notify(`Retry policy updated — up to ${updated.maxAttempts} attempt(s) before DEAD.`);
    } catch (err) {
      setPolicyError((err as Error).message);
      notify((err as Error).message, "error");
    }
  }

  useEffect(() => {
    // Attempts carry the real request/response bodies exchanged with a Target — admin-only (see
    // SecurityConfig.java), unlike the rest of this page's public delivery summary.
    if (!expandedId || !loggedIn) {
      setAttempts([]);
      return;
    }
    getAuthed<DeliveryAttempt[]>(`/api/deliveries/${expandedId}/attempts`)
      .then(setAttempts)
      .catch((err) => setError((err as Error).message));
  }, [expandedId, loggedIn]);

  function toggle(id: string) {
    setExpandedId(expandedId === id ? null : id);
    setDetailAttemptId(null);
  }

  function toggleDetail(attemptId: string) {
    setDetailAttemptId(detailAttemptId === attemptId ? null : attemptId);
  }

  function toggleEvent(delivery: Delivery) {
    if (eventOpenId === delivery.id) {
      setEventOpenId(null);
      return;
    }
    setEventOpenId(delivery.id);
    setEvent(null);
    setEventError(null);
    getAuthed<CanonicalEvent>(`/api/events/${delivery.eventId}`)
      .then(setEvent)
      .catch((err) => setEventError((err as Error).message));
  }

  async function replay(id: string) {
    if (!confirm("Replay this Delivery? This makes one more real attempt against the live Target.")) return;
    setError(null);
    try {
      await post(`/api/deliveries/${id}/replay`, null);
      reload();
      notify("Replay triggered.");
    } catch (err) {
      setError((err as Error).message);
      notify((err as Error).message, "error");
    }
  }

  return (
    <div>
      <h1>Deliveries</h1>
      {error && <p className="error">{error}</p>}

      <div className="card form-card" style={{ marginBottom: "1.25rem" }}>
        {!editingPolicy ? (
          <div className="page-header" style={{ marginBottom: 0 }}>
            <span>
              Retry policy: up to <strong>{maxAttempts ?? "?"}</strong> attempt(s) before a Delivery is marked{" "}
              <strong>DEAD</strong> (DLQ).
            </span>
            {loggedIn && maxAttempts !== null && (
              <button
                onClick={() => {
                  setPolicyForm(String(maxAttempts));
                  setPolicyError(null);
                  setEditingPolicy(true);
                }}
              >
                Edit
              </button>
            )}
          </div>
        ) : (
          <form onSubmit={savePolicy} className="filter-row" style={{ alignItems: "flex-end" }}>
            <label>
              Max attempts before DEAD
              <input
                type="number"
                min={1}
                max={10}
                required
                value={policyForm}
                onChange={(e) => setPolicyForm(e.target.value)}
              />
            </label>
            <button type="submit" className="btn-primary">
              Save
            </button>
            <button type="button" onClick={() => setEditingPolicy(false)}>
              Cancel
            </button>
            {policyError && <p className="error">{policyError}</p>}
          </form>
        )}
      </div>

      <div className="filter-row">
        <label>
          State
          <select value={stateFilter} onChange={(e) => setStateFilter(e.target.value as DeliveryState | "ALL")}>
            {STATES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
        <span className="muted">Showing the most recent {deliveries.length} (capped at 200).</span>
      </div>

      <table>
        <thead>
          <tr>
            <th>State</th>
            <th>Target</th>
            <th>Attempts</th>
            <th>Updated</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {deliveries.map((d) => {
            const targetKey = targetKeyById[d.targetId] ?? d.targetId.slice(0, 8);
            return (
              <Fragment key={d.id}>
                <tr className={d.id === highlight ? "row-highlight" : undefined}>
                  <td>
                    <StatusBadge value={d.state} />
                  </td>
                  <td>
                    <code>{targetKey}</code>
                  </td>
                  <td>{d.attemptCount}</td>
                  <td>{new Date(d.updatedAt).toLocaleString()}</td>
                  <td>
                    {loggedIn ? (
                      <button onClick={() => toggle(d.id)}>{expandedId === d.id ? "Hide" : "Attempts"}</button>
                    ) : (
                      <span className="muted">Log in to view attempts</span>
                    )}
                    {loggedIn && (
                      <button onClick={() => toggleEvent(d)}>{eventOpenId === d.id ? "Hide event" : "Source event"}</button>
                    )}
                    {loggedIn && d.state === "DEAD" && <button onClick={() => replay(d.id)}>Replay</button>}
                  </td>
                </tr>
                {eventOpenId === d.id && loggedIn && (
                  <tr>
                    <td colSpan={5}>
                      {eventError && <p className="error">{eventError}</p>}
                      {!eventError && !event && <p className="muted">Loading event...</p>}
                      {event && <EventDetail event={event} />}
                    </td>
                  </tr>
                )}
                {expandedId === d.id && loggedIn && (
                  <tr>
                    <td colSpan={5}>
                      <p className="muted" style={{ marginTop: 0 }}>
                        RelayHub's own {attempts.length || d.attemptCount} attempt(s) to deliver this event to Target{" "}
                        <code>{targetKey}</code> — every row below is a retry against that <em>same</em> Target, not a
                        chain through different systems. RelayHub gives up and marks the delivery <strong>DEAD</strong>{" "}
                        after {maxAttempts ?? "a few"} failed attempts; <strong>Replay</strong> above makes one more.
                      </p>
                      <table className="nested-table">
                        <thead>
                          <tr>
                            <th>Attempt #</th>
                            <th>Status</th>
                            <th>HTTP</th>
                            <th>Error (from {targetKey})</th>
                            <th>At</th>
                            <th></th>
                          </tr>
                        </thead>
                        <tbody>
                          {attempts.map((a) => (
                            <Fragment key={a.id}>
                              <tr>
                                <td>{a.attemptNumber}</td>
                                <td>
                                  <StatusBadge value={a.status} />
                                </td>
                                <td>{a.httpStatus ?? "-"}</td>
                                <td>{a.errorMessage ?? "-"}</td>
                                <td>{new Date(a.attemptedAt).toLocaleString()}</td>
                                <td>
                                  <button onClick={() => toggleDetail(a.id)}>
                                    {detailAttemptId === a.id ? "Hide" : "Request/Response"}
                                  </button>
                                </td>
                              </tr>
                              {detailAttemptId === a.id && (
                                <tr>
                                  <td colSpan={6}>
                                    <AttemptDetail attempt={a} />
                                  </td>
                                </tr>
                              )}
                            </Fragment>
                          ))}
                        </tbody>
                      </table>
                    </td>
                  </tr>
                )}
              </Fragment>
            );
          })}
        </tbody>
      </table>
      {loading && <p className="muted">Loading Deliveries...</p>}
      {!loading && deliveries.length === 0 && <p className="muted">No Deliveries yet.</p>}
    </div>
  );
}
