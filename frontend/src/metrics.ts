import { get } from "./api";

interface PrometheusRangeResult {
  data: {
    result: {
      metric: Record<string, string>;
      values: [number, string][];
    }[];
  };
}

export interface ChartPoint {
  time: string;
  [series: string]: string | number;
}

/**
 * Runs a Prometheus range query (via the backend proxy — see metrics/MetricsController.java,
 * the browser never calls Prometheus directly) over the last `rangeMinutes` and reshapes each
 * returned series into one row per timestamp, keyed by `seriesLabel(metric)` — the shape
 * recharts wants (one array of {time, seriesA, seriesB, ...} objects).
 */
export async function queryRange(
  query: string,
  rangeMinutes: number,
  stepSeconds: number,
  seriesLabel: (metric: Record<string, string>) => string
): Promise<ChartPoint[]> {
  const end = Math.floor(Date.now() / 1000);
  const start = end - rangeMinutes * 60;
  const params = new URLSearchParams({
    query,
    start: String(start),
    end: String(end),
    step: String(stepSeconds),
  });
  const res = await get<PrometheusRangeResult>(`/api/metrics/query_range?${params}`);

  const byTime = new Map<number, ChartPoint>();
  for (const series of res.data.result) {
    const label = seriesLabel(series.metric);
    for (const [ts, value] of series.values) {
      const point = byTime.get(ts) ?? { time: new Date(ts * 1000).toLocaleTimeString() };
      point[label] = Number(value);
      byTime.set(ts, point);
    }
  }
  return [...byTime.entries()].sort(([a], [b]) => a - b).map(([, point]) => point);
}
