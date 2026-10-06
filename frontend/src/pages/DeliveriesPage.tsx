import { Fragment, FormEvent, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { get, getAuthed, put, post } from "../api";
import { isLoggedIn } from "../auth";
import { CanonicalEvent, Delivery, DeliveryAttempt, DeliverySettings, DeliveryState, Subscription, Target } from "../types";
import { StatusBadge } from "../components/StatusBadge";
import { AttemptDetail } from "../components/AttemptDetail";
import { EventDetail } from "../components/EventDetail";
import { useToast } from "../toast";

const STATES: (DeliveryState | "ALL")[] = ["ALL", "PENDING", "PROCESSING", "RETRYING", "SUCCEEDED", "DEAD", "REPLAYING"];

export function DeliveriesPage() {
  const [params] = useSearchParams();
  const highlight = params.get("highlight");
  const [stateFilter, setStateFilter] = useState<DeliveryState | "ALL">("ALL");
  const [targetFilter, setTargetFilter] = useState<string>("ALL");
  const [subscriptionFilter, setSubscriptionFilter] = useState<string>("ALL");
  const [deliveries, setDeliveries] = useState<Delivery[]>([]);
  const [loading, setLoading] = useState(true);
  const [targets, setTargets] = useState<Target[]>([]);
  const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
  const [expandedId, setExpandedId] = useState<string | null>(highlight);
  const [attempts, setAttempts] = useState<DeliveryAttempt[]>([]);
  const [detailAttemptId, setDetailAttemptId] = useState<string | null>(null);
  const [eventOpenId, setEventOpenId] = useState<string | null>(null);
  const [event, setEvent] = useState<CanonicalEvent | null>(null);
  const [eventError, setEventError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [maxAttempts, setMaxAttempts] = useState<number | null>(null);
  const [autoReplayIntervalMs, setAutoReplayIntervalMs] = useState<number | null>(null);
  const [editingPolicy, setEditingPolicy] = useState(false);
  const [maxAttemptsForm, setMaxAttemptsForm] = useState("");
  const [autoReplaySecondsForm, setAutoReplaySecondsForm] = useState("");
  const [policyError, setPolicyError] = useState<string | null>(null);
  const loggedIn = isLoggedIn();
  const { notify } = useToast();

  const targetKeyById = useMemo(() => Object.fromEntries(targets.map((t) => [t.id, t.key])), [targets]);

  // Subscription dropdown narrows to the selected Target's own Subscriptions (same cascading
  // pattern as the Subscriptions-page create form) — picking a Subscription that belongs to a
  // different Target than the one already selected would silently return zero rows.
  const subscriptionsForTarget = useMemo(() => {
    if (targetFilter === "ALL") return subscriptions;
    const targetKey = targetKeyById[targetFilter];
    return subscriptions.filter((s) => s.targetKey === targetKey);
  }, [subscriptions, targetFilter, targetKeyById]);

  // Reacts to the ?highlight= param changing while already mounted on this route (react-router
  // reuses the component instance across search-param-only navigations, so the useState initial
  // value above only covers the first mount).
  useEffect(() => {
    if (highlight) setExpandedId(highlight);
  }, [highlight]);

  function reload() {
    setLoading(true);
    const params = new URLSearchParams();
    if (subscriptionFilter !== "ALL") params.set("subscriptionId", subscriptionFilter);
    else if (targetFilter !== "ALL") params.set("targetId", targetFilter);
    if (stateFilter !== "ALL") params.set("state", stateFilter);
    const query = params.toString();
    get<Delivery[]>(`/api/deliveries${query ? `?${query}` : ""}`)
      .then(setDeliveries)
      .catch((err) => setError((err as Error).message))
      .finally(() => setLoading(false));
  }

  useEffect(reload, [stateFilter, targetFilter, subscriptionFilter]);
  useEffect(() => {
    get<Target[]>("/api/targets").then(setTargets).catch(() => {});
    get<Subscription[]>("/api/subscriptions").then(setSubscriptions).catch(() => {});
  }, []);

  // Changing the Target filter to one that doesn't include the currently-selected Subscription
  // (or clearing it to "ALL") drops the now-invalid Subscription selection rather than silently
  // querying for a combination that can't match anything.
  useEffect(() => {
    if (subscriptionFilter === "ALL") return;
    if (!subscriptionsForTarget.some((s) => s.id === subscriptionFilter)) {
      setSubscriptionFilter("ALL");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only react to targetFilter changing, not every subscriptionsForTarget identity change (would refire on every unrelated reload)
  }, [targetFilter]);

  function loadPolicy() {
    get<DeliverySettings>("/api/delivery-settings")
      .then((s) => {
        setMaxAttempts(s.maxAttempts);
        setAutoReplayIntervalMs(s.autoReplayIntervalMs);
      })
      .catch(() => {});
  }

  useEffect(loadPolicy, []);

  async function savePolicy(e: FormEvent) {
    e.preventDefault();
    setPolicyError(null);
    const parsedMaxAttempts = Number(maxAttemptsForm);
    const parsedIntervalMs = Number(autoReplaySecondsForm) * 1000;
    try {
      const updated = await put<DeliverySettings>("/api/delivery-settings", {
        maxAttempts: parsedMaxAttempts,
        autoReplayIntervalMs: parsedIntervalMs,
      });
      setMaxAttempts(updated.maxAttempts);
      setAutoReplayIntervalMs(updated.autoReplayIntervalMs);
      setEditingPolicy(false);
      notify(
        `Retry policy updated — up to ${updated.maxAttempts} attempt(s) before DEAD, auto-replay every ${Math.round(updated.autoReplayIntervalMs / 1000)}s.`
      );
    } catch (err) {
      setPolicyError((err as Error).message);
      notify((err as Error).message, "error");
    }
  }

  useEffect(() => {
    // Attempts carry the real request/response bodies exchanged with a Target — public, same as
    // the rest of this page's delivery summary (SecurityConfig.java reopened this 2026-09-29:
    // every attached Target today is a synthetic demo system, so there's nothing sensitive to
    // gate; revisit if a real Target is ever connected).
    if (!expandedId) {
      setAttempts([]);
      return;
    }
    get<DeliveryAttempt[]>(`/api/deliveries/${expandedId}/attempts`)
      .then(setAttempts)
      .catch((err) => setError((err as Error).message));
  }, [expandedId]);

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

  async function bulkReplay() {
    const scopeLabel =
      subscriptionFilter !== "ALL"
        ? "this Subscription's"
        : targetFilter !== "ALL"
          ? `Target "${targetKeyById[targetFilter]}"'s`
          : "all";
    if (
      !confirm(
        `Replay up to 50 of ${scopeLabel} currently-DEAD Deliveries, oldest first? Each makes one more real attempt against its live Target.`
      )
    )
      return;
    setError(null);
    try {
      const params = new URLSearchParams();
      if (subscriptionFilter !== "ALL") params.set("subscriptionId", subscriptionFilter);
      else if (targetFilter !== "ALL") params.set("targetId", targetFilter);
      const result = await post<{ attempted: number; succeeded: number; stillDead: number; errors: number }>(
        `/api/deliveries/bulk-replay${params.toString() ? `?${params}` : ""}`,
        null
      );
      reload();
      if (result.attempted === 0) {
        notify("No DEAD Deliveries to replay in this scope.");
      } else {
        notify(
          `Bulk replay: ${result.attempted} attempted, ${result.succeeded} succeeded, ${result.stillDead} still DEAD` +
            (result.errors > 0 ? `, ${result.errors} error(s)` : "") +
            "."
        );
      }
    } catch (err) {
      setError((err as Error).message);
      notify((err as Error).message, "error");
    }
  }

  const deadInView = deliveries.filter((d) => d.state === "DEAD").length;

  return (
    <div>
      <h1>Deliveries</h1>
      {error && <p className="error">{error}</p>}

      <div className="card form-card" style={{ marginBottom: "1.25rem" }}>
        {!editingPolicy ? (
          <div className="page-header" style={{ marginBottom: 0 }}>
            <span>
              Retry policy: up to <strong>{maxAttempts ?? "?"}</strong> attempt(s) before a Delivery is marked{" "}
              <strong>DEAD</strong> (DLQ). Auto-replay sweeps the DLQ every{" "}
              <strong>{autoReplayIntervalMs !== null ? Math.round(autoReplayIntervalMs / 1000) : "?"}s</strong>.
            </span>
            {loggedIn && maxAttempts !== null && autoReplayIntervalMs !== null && (
              <button
                onClick={() => {
                  setMaxAttemptsForm(String(maxAttempts));
                  setAutoReplaySecondsForm(String(Math.round(autoReplayIntervalMs / 1000)));
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
                value={maxAttemptsForm}
                onChange={(e) => setMaxAttemptsForm(e.target.value)}
              />
            </label>
            <label>
              Auto-replay every (seconds)
              <input
                type="number"
                min={5}
                max={3600}
                required
                value={autoReplaySecondsForm}
                onChange={(e) => setAutoReplaySecondsForm(e.target.value)}
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
        <label>
          Target
          <select value={targetFilter} onChange={(e) => setTargetFilter(e.target.value)}>
            <option value="ALL">All Targets</option>
            {targets.map((t) => (
              <option key={t.id} value={t.id}>
                {t.key}
              </option>
            ))}
          </select>
        </label>
        <label>
          Subscription
          <select value={subscriptionFilter} onChange={(e) => setSubscriptionFilter(e.target.value)}>
            <option value="ALL">All Subscriptions</option>
            {subscriptionsForTarget.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </label>
        <span className="muted">Showing the most recent {deliveries.length} (capped at 200).</span>
        {loggedIn && deadInView > 0 && <button onClick={bulkReplay}>Replay all DEAD in view</button>}
      </div>

      <table>
        <thead>
          <tr>
            <th>State</th>
            <th>Target</th>
            <th>Attempts</th>
            <th>Next attempt</th>
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
                  <td>{d.state === "RETRYING" && d.nextAttemptAt ? new Date(d.nextAttemptAt).toLocaleString() : "-"}</td>
                  <td>{new Date(d.updatedAt).toLocaleString()}</td>
                  <td>
                    <button onClick={() => toggle(d.id)}>{expandedId === d.id ? "Hide" : "Attempts"}</button>
                    {loggedIn && (
                      <button onClick={() => toggleEvent(d)}>{eventOpenId === d.id ? "Hide event" : "Source event"}</button>
                    )}
                    {loggedIn && d.state === "DEAD" && <button onClick={() => replay(d.id)}>Replay</button>}
                  </td>
                </tr>
                {eventOpenId === d.id && loggedIn && (
                  <tr>
                    <td colSpan={6}>
                      {eventError && <p className="error">{eventError}</p>}
                      {!eventError && !event && <p className="muted">Loading event...</p>}
                      {event && <EventDetail event={event} />}
                    </td>
                  </tr>
                )}
                {expandedId === d.id && (
                  <tr>
                    <td colSpan={6}>
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
