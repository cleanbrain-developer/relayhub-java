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

interface Point {
  x: number;
  y: number;
}

interface Pulse {
  id: number;
  from: Point;
  to: Point;
  color: string;
  start: number;
}

const HUB_X = 480;
const NODE_X_SOURCE = 110;
const NODE_X_TARGET = 850;
const NODE_WIDTH = 170;
const NODE_HEIGHT = 40;
const ROW_HEIGHT = 76;
const TOP_MARGIN = 50;
const PULSE_DURATION_MS = 900;
const PULSE_RADIUS = 8;

function layout(count: number, x: number): Point[] {
  return Array.from({ length: Math.max(count, 1) }, (_, i) => ({ x, y: TOP_MARGIN + i * ROW_HEIGHT }));
}

/**
 * A live, push-driven topology map — the maintainer asked for something like Jennifer APM's
 * real-time request monitoring rather than the 30s-poll charts on the Dashboard. Backed by
 * Server-Sent Events (see live/LiveActivityController.java): the browser never polls here, the
 * backend pushes one event per ingress call and per delivery attempt.
 *
 * Pulses are animated with a plain requestAnimationFrame loop, not SVG's declarative
 * <animateMotion> — an earlier version used that and rendered nothing visible in practice:
 * dynamically inserting/removing SMIL animation elements on every event is exactly the case
 * browsers handle inconsistently (the animation frequently just never starts on a freshly
 * mounted element). Driving cx/cy from elapsed time in React state is more code but actually
 * plays every time.
 */
export function LivePage() {
  const [sources, setSources] = useState<Source[]>([]);
  const [targets, setTargets] = useState<Target[]>([]);
  const [connected, setConnected] = useState(false);
  const [feed, setFeed] = useState<LiveEvent[]>([]);
  const [renderedPulses, setRenderedPulses] = useState<(Pulse & { progress: number })[]>([]);
  const pulsesRef = useRef<Pulse[]>([]);
  const pulseId = useRef(0);
  const rafRef = useRef<number | null>(null);

  useEffect(() => {
    get<Source[]>("/api/sources").then(setSources).catch((e) => console.error("Failed to load sources", e));
    get<Target[]>("/api/targets").then(setTargets).catch((e) => console.error("Failed to load targets", e));
  }, []);

  const sourcePositions = useMemo(() => {
    const positions = layout(sources.length, NODE_X_SOURCE);
    return Object.fromEntries(sources.map((s, i) => [s.key, positions[i]]));
  }, [sources]);

  const targetPositions = useMemo(() => {
    const positions = layout(targets.length, NODE_X_TARGET);
    return Object.fromEntries(targets.map((t, i) => [t.key, positions[i]]));
  }, [targets]);

  const rowCount = Math.max(sources.length, targets.length, 1);
  const hub: Point = useMemo(() => ({ x: HUB_X, y: TOP_MARGIN + ((rowCount - 1) * ROW_HEIGHT) / 2 }), [rowCount]);
  const svgHeight = TOP_MARGIN * 2 + (rowCount - 1) * ROW_HEIGHT + 20;

  function tick() {
    const now = performance.now();
    pulsesRef.current = pulsesRef.current.filter((p) => now - p.start < PULSE_DURATION_MS);
    setRenderedPulses(
      pulsesRef.current.map((p) => ({ ...p, progress: Math.min((now - p.start) / PULSE_DURATION_MS, 1) }))
    );
    if (pulsesRef.current.length > 0) {
      rafRef.current = requestAnimationFrame(tick);
    } else {
      rafRef.current = null;
    }
  }

  function spawnPulse(from: Point, to: Point, color: string) {
    pulsesRef.current = [...pulsesRef.current, { id: pulseId.current++, from, to, color, start: performance.now() }];
    if (rafRef.current === null) {
      rafRef.current = requestAnimationFrame(tick);
    }
  }

  useEffect(() => {
    const source = new EventSource("/api/live/stream");
    source.onopen = () => setConnected(true);
    source.onerror = () => setConnected(false);
    source.addEventListener("activity", (e) => {
      const event = JSON.parse((e as MessageEvent).data) as LiveEvent;
      setFeed((prev) => [event, ...prev].slice(0, 30));

      const from = event.stage === "ingress" ? sourcePositions[event.sourceKey] : hub;
      const to = event.stage === "ingress" ? hub : event.targetKey ? targetPositions[event.targetKey] : null;
      if (!from || !to) return;

      const color = event.status === "failed" ? "#d6293e" : event.status === "success" ? "#0f8b3f" : "#4f46e5";
      spawnPulse(from, to, color);
    });
    return () => {
      source.close();
      if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);
    };
  }, [sourcePositions, targetPositions, hub]);

  return (
    <div>
      <div className="page-header">
        <h1>Live Activity</h1>
        <span className={connected ? "badge badge-ok" : "badge badge-muted"}>
          {connected ? "● connected" : "connecting..."}
        </span>
      </div>
      <p className="muted">
        Real-time Source &rarr; RelayHub &rarr; Target traffic, pushed over SSE as it happens — no manual refresh.
      </p>

      <div className="card live-topology">
        <svg viewBox={`0 0 960 ${svgHeight}`} width="100%" height={svgHeight} preserveAspectRatio="xMidYMid meet">
          {sources.map((s) => {
            const pos = sourcePositions[s.key];
            if (!pos) return null;
            return (
              <g key={s.key}>
                <line x1={pos.x + NODE_WIDTH / 2} y1={pos.y} x2={hub.x - 66} y2={hub.y} className="topology-edge" />
                <rect
                  x={pos.x - NODE_WIDTH / 2}
                  y={pos.y - NODE_HEIGHT / 2}
                  width={NODE_WIDTH}
                  height={NODE_HEIGHT}
                  rx={8}
                  className="topology-node topology-node-source"
                />
                <text x={pos.x} y={pos.y + 5} textAnchor="middle" className="topology-label">
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
                <line x1={hub.x + 66} y1={hub.y} x2={pos.x - NODE_WIDTH / 2} y2={pos.y} className="topology-edge" />
                <rect
                  x={pos.x - NODE_WIDTH / 2}
                  y={pos.y - NODE_HEIGHT / 2}
                  width={NODE_WIDTH}
                  height={NODE_HEIGHT}
                  rx={8}
                  className="topology-node topology-node-target"
                />
                <text x={pos.x} y={pos.y + 5} textAnchor="middle" className="topology-label">
                  {t.key}
                </text>
              </g>
            );
          })}

          <rect x={hub.x - 60} y={hub.y - 24} width={120} height={48} rx={12} className="topology-hub" />
          <text x={hub.x} y={hub.y + 5} textAnchor="middle" className="topology-hub-label">
            RelayHub
          </text>

          {renderedPulses.map((p) => {
            const x = p.from.x + (p.to.x - p.from.x) * p.progress;
            const y = p.from.y + (p.to.y - p.from.y) * p.progress;
            const opacity = p.progress > 0.8 ? 1 - (p.progress - 0.8) / 0.2 : 1;
            return (
              <circle
                key={p.id}
                cx={x}
                cy={y}
                r={PULSE_RADIUS}
                fill={p.color}
                opacity={opacity}
                className="topology-pulse"
                style={{ color: p.color }}
              />
            );
          })}
        </svg>
        {sources.length === 0 && targets.length === 0 && (
          <p className="muted" style={{ textAlign: "center" }}>
            No Sources/Targets registered yet.
          </p>
        )}
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
