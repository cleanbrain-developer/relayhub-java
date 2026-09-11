import { useEffect, useState } from "react";
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { ChartPoint, queryRange } from "../metrics";

const COLORS = ["#2f5cf0", "#cf222e", "#1a7f37", "#a15c00", "#8250df"];

interface Props {
  title: string;
  query: string;
  seriesLabel: (metric: Record<string, string>) => string;
  rangeMinutes?: number;
  stepSeconds?: number;
}

export function MetricsChart({ title, query, seriesLabel, rangeMinutes = 60, stepSeconds = 30 }: Props) {
  const [points, setPoints] = useState<ChartPoint[]>([]);
  const [seriesKeys, setSeriesKeys] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    function load() {
      queryRange(query, rangeMinutes, stepSeconds, seriesLabel)
        .then((data) => {
          if (cancelled) return;
          setPoints(data);
          const keys = new Set<string>();
          data.forEach((p) => Object.keys(p).forEach((k) => k !== "time" && keys.add(k)));
          setSeriesKeys([...keys]);
          setError(null);
        })
        .catch((err) => !cancelled && setError((err as Error).message));
    }

    load();
    // Prometheus was added specifically so this chart has real history to show, not just a live
    // snapshot — refetching periodically keeps it moving without a full page reload.
    const interval = setInterval(load, 30_000);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [query, rangeMinutes, stepSeconds]);

  return (
    <div className="chart-card">
      <h3>{title}</h3>
      {error ? (
        <p className="muted">No data yet ({error}).</p>
      ) : points.length === 0 ? (
        <p className="muted">Loading...</p>
      ) : (
        <ResponsiveContainer width="100%" height={220}>
          <LineChart data={points}>
            <CartesianGrid strokeDasharray="3 3" stroke="#eee" />
            <XAxis dataKey="time" tick={{ fontSize: 11 }} minTickGap={40} />
            <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
            <Tooltip />
            <Legend wrapperStyle={{ fontSize: 12 }} />
            {seriesKeys.map((key, i) => (
              <Line
                key={key}
                type="monotone"
                dataKey={key}
                stroke={COLORS[i % COLORS.length]}
                dot={false}
                strokeWidth={2}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      )}
    </div>
  );
}
