import { get } from "./api";

interface PrometheusRangeResult {
  data: {
    result: {
      metric: Record<string, string>;
      values: [number, string][];
    }[];
  };
}

interface PrometheusInstantResult {
  data: {
    result: {
      metric: Record<string, string>;
      value: [number, string];
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

/**
 * Runs a Prometheus instant query (GET /api/metrics/query — same proxy as queryRange, previously
 * unused by the frontend even though the backend has had it since Spec 004) and sums every
 * returned series' value, since callers here use a `sum by (label) (...)` query and just want one
 * "right now" total rather than the per-label breakdown queryRange's charts already show. Returns
 * null when Prometheus has no data yet (e.g. right after a fresh deploy) rather than 0, so the
 * caller can distinguish "genuinely zero" from "no data".
 */
export async function queryInstant(query: string): Promise<number | null> {
  const res = await get<PrometheusInstantResult>(`/api/metrics/query?${new URLSearchParams({ query })}`);
  if (res.data.result.length === 0) return null;
  return res.data.result.reduce((sum, series) => sum + Number(series.value[1]), 0);
}
