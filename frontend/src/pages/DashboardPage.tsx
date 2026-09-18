import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { get } from "../api";
import { queryInstant } from "../metrics";
import { Delivery, DeliverySummary, Target } from "../types";
import { StatusBadge } from "../components/StatusBadge";
import { MetricsChart } from "../components/MetricsChart";

export function DashboardPage() {
  const [summary, setSummary] = useState<DeliverySummary | null>(null);
  const [recent, setRecent] = useState<Delivery[]>([]);
  const [loading, setLoading] = useState(true);
  const [targets, setTargets] = useState<Target[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [ingressRate, setIngressRate] = useState<number | null>(null);
  const [deliveryRate, setDeliveryRate] = useState<number | null>(null);

  const targetKeyById = useMemo(() => Object.fromEntries(targets.map((t) => [t.id, t.key])), [targets]);

  useEffect(() => {
    Promise.all([get<DeliverySummary>("/api/deliveries/summary"), get<Delivery[]>("/api/deliveries")])
      .then(([s, deliveries]) => {
        setSummary(s);
        setRecent(deliveries.slice(0, 10));
      })
      .catch((err) => setError((err as Error).message))
      .finally(() => setLoading(false));
    get<Target[]>("/api/targets")
      .then(setTargets)
      .catch(() => setError("Couldn't load Targets — delivery rows below will show raw IDs instead of Target keys."));
    // A one-off "right now" total alongside the two charts below (which already show the same
    // rates broken down by outcome/status over time) — uses GET /api/metrics/query, unused by the
    // frontend until now despite existing since Spec 004.
    queryInstant("sum(rate(relayhub_ingress_events_total[5m]) * 60)").then(setIngressRate).catch(() => {});
    queryInstant("sum(rate(relayhub_delivery_attempts_total[5m]) * 60)").then(setDeliveryRate).catch(() => {});
  }, []);

  return (
    <div>
      <h1>Dashboard</h1>
      {error && <p className="error">{error}</p>}
      {loading && <p className="muted">Loading...</p>}
      {summary && (
        <div className="stat-row">
          <div className="stat-tile">
            <div className="stat-value">{summary.succeeded}</div>
            <div className="stat-label">Succeeded</div>
          </div>
          <div className="stat-tile">
            <div className="stat-value">{summary.pending}</div>
            <div className="stat-label">Pending</div>
          </div>
          <div className="stat-tile stat-danger">
            <div className="stat-value">{summary.dead}</div>
            <div className="stat-label">Dead (DLQ)</div>
          </div>
          {ingressRate !== null && (
            <div className="stat-tile">
              <div className="stat-value">{ingressRate.toFixed(1)}</div>
              <div className="stat-label">Ingress / min (now)</div>
            </div>
          )}
          {deliveryRate !== null && (
            <div className="stat-tile">
              <div className="stat-value">{deliveryRate.toFixed(1)}</div>
              <div className="stat-label">Delivery attempts / min (now)</div>
            </div>
          )}
        </div>
      )}

      <div className="chart-row">
        <MetricsChart
          title="Delivery attempts / min"
          query='sum by (status) (rate(relayhub_delivery_attempts_total[5m]) * 60)'
          seriesLabel={(m) => m.status ?? "unknown"}
        />
        <MetricsChart
          title="Ingress events / min"
          query='sum by (outcome) (rate(relayhub_ingress_events_total[5m]) * 60)'
          seriesLabel={(m) => m.outcome ?? "unknown"}
        />
      </div>

      <h2>Recent Deliveries</h2>
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
          {recent.map((d) => (
            <tr key={d.id}>
              <td>
                <StatusBadge value={d.state} />
              </td>
              <td>
                <code>{targetKeyById[d.targetId] ?? d.targetId.slice(0, 8)}</code>
              </td>
              <td>{d.attemptCount}</td>
              <td>{new Date(d.updatedAt).toLocaleString()}</td>
              <td>
                <Link to={`/deliveries?highlight=${d.id}`}>view</Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p>
        <Link to="/deliveries">See all deliveries &rarr;</Link>
      </p>
    </div>
  );
}
