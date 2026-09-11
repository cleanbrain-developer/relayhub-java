import { useEffect, useMemo, useRef, useState } from "react";
import { get } from "../api";
import { Source, Target } from "../types";

interface LiveEvent {
  stage: "ingress" | "delivery";
  sourceKey: string;
  targetKey: string | null;
  status: "success" | "failed" | null;
  at: string;
}

interface Pulse {
  id: number;
  path: string;
  color: string;
}

const HUB_X = 480;
const NODE_X_SOURCE = 90;
const NODE_X_TARGET = 870;
const NODE_WIDTH = 150;
const ROW_HEIGHT = 64;
const TOP_MARGIN = 40;

function layout(count: number, x: number): { key: string; x: number; y: number }[] {
  return Array.from({ length: count }, (_, i) => ({ key: "", x, y: TOP_MARGIN + i * ROW_HEIGHT + 20 }));
}

/**
 * A live, push-driven topology map — the maintainer asked for something like Jennifer APM's
 * real-time request monitoring rather than the 30s-poll charts on the Dashboard. Backed by
 * Server-Sent Events (see live/LiveActivityController.java): the browser never polls here, the
 * backend pushes one event per ingress call and per delivery attempt, and this page animates a
 * pulse along the matching Source->RelayHub or RelayHub->Target edge as each one arrives.
 */
export function LivePage() {
  const [sources, setSources] = useState<Source[]>([]);
  const [targets, setTargets] = useState<Target[]>([]);
  const [connected, setConnected] = useState(false);
  const [feed, setFeed] = useState<LiveEvent[]>([]);
  const [pulses, setPulses] = useState<Pulse[]>([]);
  const pulseId = useRef(0);

  useEffect(() => {
    get<Source[]>("/api/sources").then(setSources).catch(() => {});
    get<Target[]>("/api/targets").then(setTargets).catch(() => {});
  }, []);

  const sourcePositions = useMemo(() => {
    const positions = layout(sources.length, NODE_X_SOURCE);
    return Object.fromEntries(sources.map((s, i) => [s.key, positions[i]]));
  }, [sources]);

  const targetPositions = useMemo(() => {
    const positions = layout(targets.length, NODE_X_TARGET);
    return Object.fromEntries(targets.map((t, i) => [t.key, positions[i]]));
  }, [targets]);

  const hubY = useMemo(() => {
    const rows = Math.max(sources.length, targets.length, 1);
    return TOP_MARGIN + ((rows - 1) * ROW_HEIGHT) / 2 + 20;
  }, [sources.length, targets.length]);

  const svgHeight = Math.max(sources.length, targets.length, 1) * ROW_HEIGHT + TOP_MARGIN + 20;

  useEffect(() => {
    const source = new EventSource("/api/live/stream");
    source.onopen = () => setConnected(true);
    source.onerror = () => setConnected(false);
    source.addEventListener("activity", (e) => {
      const event = JSON.parse((e as MessageEvent).data) as LiveEvent;
      setFeed((prev) => [event, ...prev].slice(0, 30));

      const from = event.stage === "ingress" ? sourcePositions[event.sourceKey] : { x: HUB_X, y: hubY };
      const to =
        event.stage === "ingress" ? { x: HUB_X, y: hubY } : event.targetKey ? targetPositions[event.targetKey] : null;
      if (!from || !to) return;

      const path = `M ${from.x} ${from.y} L ${to.x} ${to.y}`;
      const color = event.status === "failed" ? "#d6293e" : event.status === "success" ? "#0f8b3f" : "#4f46e5";
      const id = pulseId.current++;
      setPulses((prev) => [...prev, { id, path, color }]);
      setTimeout(() => setPulses((prev) => prev.filter((p) => p.id !== id)), 1100);
    });
    return () => source.close();
  }, [sourcePositions, targetPositions, hubY]);

  return (
    <div>
      <div className="page-header">
        <h1>Live Activity</h1>
        <span className={connected ? "badge badge-ok" : "badge badge-muted"}>
          {connected ? "connected" : "connecting..."}
        </span>
      </div>
      <p className="muted">
        Real-time Source &rarr; RelayHub &rarr; Target traffic, pushed over SSE as it happens — no manual refresh.
      </p>

      <div className="card" style={{ overflowX: "auto" }}>
        <svg viewBox={`0 0 960 ${svgHeight}`} width="100%" height={svgHeight} style={{ minWidth: 700 }}>
          {sources.map((s) => {
            const pos = sourcePositions[s.key];
            if (!pos) return null;
            return (
              <g key={s.key}>
                <line x1={pos.x + NODE_WIDTH / 2} y1={pos.y} x2={HUB_X - 60} y2={hubY} stroke="#e6e7ee" strokeWidth={2} />
                <rect
                  x={pos.x - NODE_WIDTH / 2}
                  y={pos.y - 16}
                  width={NODE_WIDTH}
                  height={32}
                  rx={8}
                  fill="#fff"
                  stroke="#e6e7ee"
                />
                <text x={pos.x} y={pos.y + 5} textAnchor="middle" fontSize={12}>
                  {s.key}
                </text>
              </g>
            );
          })}

          {targets.map((t) => {
            const pos = targetPositions[t.key];
            if (!pos) return null;
            return (
              <g key={t.key}>
                <line x1={HUB_X + 60} y1={hubY} x2={pos.x - NODE_WIDTH / 2} y2={pos.y} stroke="#e6e7ee" strokeWidth={2} />
                <rect
                  x={pos.x - NODE_WIDTH / 2}
                  y={pos.y - 16}
                  width={NODE_WIDTH}
                  height={32}
                  rx={8}
                  fill="#fff"
                  stroke="#e6e7ee"
                />
                <text x={pos.x} y={pos.y + 5} textAnchor="middle" fontSize={12}>
                  {t.key}
                </text>
              </g>
            );
          })}

          <rect x={HUB_X - 60} y={hubY - 20} width={120} height={40} rx={10} fill="#4f46e5" />
          <text x={HUB_X} y={hubY + 5} textAnchor="middle" fontSize={13} fill="#fff" fontWeight={600}>
            RelayHub
          </text>

          {pulses.map((p) => (
            <circle key={p.id} r={5} fill={p.color}>
              <animateMotion dur="1s" repeatCount="1" path={p.path} fill="freeze" />
              <animate attributeName="opacity" values="1;1;0" keyTimes="0;0.85;1" dur="1s" repeatCount="1" fill="freeze" />
            </circle>
          ))}
        </svg>
      </div>

      <h2>Recent activity</h2>
      <table>
        <thead>
          <tr>
            <th>Stage</th>
            <th>Source</th>
            <th>Target</th>
            <th>Status</th>
            <th>At</th>
          </tr>
        </thead>
        <tbody>
          {feed.map((e, i) => (
            <tr key={i}>
              <td>{e.stage}</td>
              <td>{e.sourceKey}</td>
              <td>{e.targetKey ?? "-"}</td>
              <td>
                {e.status && (
                  <span className={`badge ${e.status === "success" ? "badge-ok" : "badge-danger"}`}>{e.status}</span>
                )}
              </td>
              <td>{new Date(e.at).toLocaleTimeString()}</td>
            </tr>
          ))}
          {feed.length === 0 && (
            <tr>
              <td colSpan={5} className="muted">
                Waiting for activity...
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
