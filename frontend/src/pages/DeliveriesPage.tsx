import { Fragment, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { get, post } from "../api";
import { isLoggedIn } from "../auth";
import { Delivery, DeliveryAttempt, DeliveryState, Target } from "../types";
import { StatusBadge } from "../components/StatusBadge";

const STATES: (DeliveryState | "ALL")[] = ["ALL", "PENDING", "SUCCEEDED", "DEAD"];

export function DeliveriesPage() {
  const [params] = useSearchParams();
  const highlight = params.get("highlight");
  const [stateFilter, setStateFilter] = useState<DeliveryState | "ALL">("ALL");
  const [deliveries, setDeliveries] = useState<Delivery[]>([]);
  const [targets, setTargets] = useState<Target[]>([]);
  const [expandedId, setExpandedId] = useState<string | null>(highlight);
  const [attempts, setAttempts] = useState<DeliveryAttempt[]>([]);
  const [error, setError] = useState<string | null>(null);
  const loggedIn = isLoggedIn();

  const targetKeyById = useMemo(() => Object.fromEntries(targets.map((t) => [t.id, t.key])), [targets]);

  // Reacts to the ?highlight= param changing while already mounted on this route (react-router
  // reuses the component instance across search-param-only navigations, so the useState initial
  // value above only covers the first mount).
  useEffect(() => {
    if (highlight) setExpandedId(highlight);
  }, [highlight]);

  function reload() {
    const query = stateFilter === "ALL" ? "" : `?state=${stateFilter}`;
    get<Delivery[]>(`/api/deliveries${query}`).then(setDeliveries).catch((err) => setError((err as Error).message));
  }

  useEffect(reload, [stateFilter]);
  useEffect(() => {
    get<Target[]>("/api/targets").then(setTargets).catch(() => {});
  }, []);

  useEffect(() => {
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
  }

  async function replay(id: string) {
    setError(null);
    try {
      await post(`/api/deliveries/${id}/replay`, null);
      reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  return (
    <div>
      <h1>Deliveries</h1>
      {error && <p className="error">{error}</p>}

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
                    <button onClick={() => toggle(d.id)}>{expandedId === d.id ? "Hide" : "Attempts"}</button>
                    {loggedIn && d.state === "DEAD" && <button onClick={() => replay(d.id)}>Replay</button>}
                  </td>
                </tr>
                {expandedId === d.id && (
                  <tr>
                    <td colSpan={5}>
                      <p className="muted" style={{ marginTop: 0 }}>
                        RelayHub's own {attempts.length || d.attemptCount} attempt(s) to deliver this event to Target{" "}
                        <code>{targetKey}</code> — every row below is a retry against that <em>same</em> Target, not a
                        chain through different systems. RelayHub gives up and marks the delivery <strong>DEAD</strong>{" "}
                        after 3 failed attempts; <strong>Replay</strong> above makes one more.
                      </p>
                      <table className="nested-table">
                        <thead>
                          <tr>
                            <th>Attempt #</th>
                            <th>Status</th>
                            <th>HTTP</th>
                            <th>Error (from {targetKey})</th>
                            <th>At</th>
                          </tr>
                        </thead>
                        <tbody>
                          {attempts.map((a) => (
                            <tr key={a.id}>
                              <td>{a.attemptNumber}</td>
                              <td>
                                <StatusBadge value={a.status} />
                              </td>
                              <td>{a.httpStatus ?? "-"}</td>
                              <td>{a.errorMessage ?? "-"}</td>
                              <td>{new Date(a.attemptedAt).toLocaleString()}</td>
                            </tr>
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
    </div>
  );
}
