import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { get } from "../api";
import { Delivery, DeliverySummary } from "../types";
import { StatusBadge } from "../components/StatusBadge";
import { MetricsChart } from "../components/MetricsChart";

export function DashboardPage() {
  const [summary, setSummary] = useState<DeliverySummary | null>(null);
  const [recent, setRecent] = useState<Delivery[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([get<DeliverySummary>("/api/deliveries/summary"), get<Delivery[]>("/api/deliveries")])
      .then(([s, deliveries]) => {
        setSummary(s);
        setRecent(deliveries.slice(0, 10));
      })
      .catch((err) => setError((err as Error).message));
  }, []);

  return (
    <div>
      <h1>Dashboard</h1>
      {error && <p className="error">{error}</p>}
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
