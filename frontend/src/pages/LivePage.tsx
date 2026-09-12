import { useEffect, useMemo, useRef, useState } from "react";
import { get, getAuthed, post } from "../api";
import { isLoggedIn } from "../auth";
import { Source, Subscription, Target } from "../types";

interface LiveEvent {
  stage: "ingress" | "delivery";
  sourceKey: string;
  eventKey: string | null;
  targetKey: string | null;
  status: "success" | "failed" | null;
  at: string;
}

interface Point {
  x: number;
  y: number;
}

interface EventNode {
  key: string;
  sourceKey: string;
  eventKey: string;
}

interface Pulse {
  id: number;
  from: Point;
  to: Point;
  color: string;
  start: number;
  /** Perpendicular offset (px) of the quadratic-bezier control point — a random arc per pulse,
   *  positive or negative, so consecutive missiles along the same edge don't overlap in a
   *  perfectly straight line. */
  arc: number;
}

const NODE_X_SOURCE = 85;
const NODE_X_EVENT = 320;
const HUB_X = 555;
const NODE_X_TARGET = 855;
const NODE_WIDTH_SOURCE = 140;
const NODE_WIDTH_EVENT = 150;
const NODE_WIDTH_TARGET = 170;
const NODE_HEIGHT = 40;
const ROW_HEIGHT = 76;
const TOP_MARGIN = 50;
const PULSE_DURATION_MS = 950;
const PULSE_RADIUS = 7;

function layout(count: number, x: number): Point[] {
  return Array.from({ length: Math.max(count, 1) }, (_, i) => ({ x, y: TOP_MARGIN + i * ROW_HEIGHT }));
}

function eventNodeKey(sourceKey: string, eventKey: string): string {
  return `${sourceKey}:${eventKey}`;
}

interface SchedulerStatus {
  running: boolean;
  intervalMs: number;
  maxLiveFlights: number;
}

/**
 * A live, push-driven topology map — the maintainer asked for something like Jennifer APM's
 * real-time request monitoring rather than the 30s-poll charts on the Dashboard. Backed by
 * Server-Sent Events (see live/LiveActivityController.java): the browser never polls here, the
 * backend pushes one event per ingress call and per delivery attempt.
 *
 * Source -> Event -> RelayHub -> Target (four columns, not three) — an earlier version collapsed
 * every Source Event under one Source into a single edge, which looked deceptively simpler than
 * the actual Subscription graph (see SubscriptionsPage: N Source Events x M Targets is N*M rows
 * there, but was one line here). The Event column is derived straight from active Subscriptions,
 * so the fan-out you see here always matches what Subscriptions shows.
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
  const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
  const [connected, setConnected] = useState(false);
  const [feed, setFeed] = useState<LiveEvent[]>([]);
  const [simulator, setSimulator] = useState<SchedulerStatus | null>(null);
  const [simulatorBusy, setSimulatorBusy] = useState(false);
  const [simulatorError, setSimulatorError] = useState<string | null>(null);
  const loggedIn = isLoggedIn();
  const [renderedPulses, setRenderedPulses] = useState<(Pulse & { progress: number })[]>([]);
  const pulsesRef = useRef<Pulse[]>([]);
  const pulseId = useRef(0);
  const rafRef = useRef<number | null>(null);

  useEffect(() => {
    // ACTIVE only — a deactivated Source/Target can't actually produce traffic (Subscriptions
    // built on it are excluded from delivery too), so it has no place on a *live* traffic map.
    get<Source[]>("/api/sources")
      .then((all) => setSources(all.filter((s) => s.status === "ACTIVE")))
      .catch((e) => console.error("Failed to load sources", e));
    get<Target[]>("/api/targets")
      .then((all) => setTargets(all.filter((t) => t.status === "ACTIVE")))
      .catch((e) => console.error("Failed to load targets", e));
    get<Subscription[]>("/api/subscriptions")
      .then((all) => setSubscriptions(all.filter((s) => s.status === "ACTIVE")))
      .catch((e) => console.error("Failed to load subscriptions", e));
  }, []);

  useEffect(() => {
    if (!loggedIn) return;
    getAuthed<SchedulerStatus>("/api/simulator/status")
      .then(setSimulator)
      .catch((e) => console.error("Failed to load simulator status", e));
  }, [loggedIn]);

  async function toggleSimulator() {
    if (!simulator || simulatorBusy) return;
    setSimulatorBusy(true);
    setSimulatorError(null);
    try {
      const next = await post<{ running: boolean }>(
        simulator.running ? "/api/simulator/pause" : "/api/simulator/resume",
        undefined
      );
      setSimulator((prev) => (prev ? { ...prev, running: next.running } : prev));
    } catch (e) {
      setSimulatorError((e as Error).message);
    } finally {
      setSimulatorBusy(false);
    }
  }

  const sourcePositions = useMemo(() => {
    const positions = layout(sources.length, NODE_X_SOURCE);
    return Object.fromEntries(sources.map((s, i) => [s.key, positions[i]]));
  }, [sources]);

  // One node per distinct (Source, Source Event) pair actually wired up by an active
  // Subscription — this is what makes the diagram's fan-out match SubscriptionsPage's row count
  // instead of hiding it behind a single Source->Hub line.
  const eventNodes = useMemo<EventNode[]>(() => {
    const seen = new Map<string, EventNode>();
    subscriptions.forEach((s) => {
      const key = eventNodeKey(s.sourceKey, s.sourceEventKey);
      if (!seen.has(key)) {
        seen.set(key, { key, sourceKey: s.sourceKey, eventKey: s.sourceEventKey });
      }
    });
    return Array.from(seen.values());
  }, [subscriptions]);

  const eventPositions = useMemo(() => {
    const positions = layout(eventNodes.length, NODE_X_EVENT);
    return Object.fromEntries(eventNodes.map((n, i) => [n.key, positions[i]]));
  }, [eventNodes]);

  const targetPositions = useMemo(() => {
    const positions = layout(targets.length, NODE_X_TARGET);
    return Object.fromEntries(targets.map((t, i) => [t.key, positions[i]]));
  }, [targets]);

  const rowCount = Math.max(sources.length, eventNodes.length, targets.length, 1);
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
    // Random arc (not always the same straight line) and slightly randomized duration/scale give
    // each "missile" its own flight instead of a mechanical, identical repeat every time.
    const arc = (Math.random() - 0.5) * 60;
    pulsesRef.current = [
      ...pulsesRef.current,
      { id: pulseId.current++, from, to, color, start: performance.now(), arc },
    ];
    if (rafRef.current === null) {
      rafRef.current = requestAnimationFrame(tick);
    }
  }

  /** Position + heading along pulse `p`'s curved flight path at animation progress `t` (0..1) —
   *  a quadratic bezier through `p.arc`'s perpendicular offset, not a straight line. */
  function pointOnArc(p: Pulse, t: number): { x: number; y: number; angleDeg: number } {
    const mx = (p.from.x + p.to.x) / 2;
    const my = (p.from.y + p.to.y) / 2;
    const dx = p.to.x - p.from.x;
    const dy = p.to.y - p.from.y;
    const len = Math.hypot(dx, dy) || 1;
    // Perpendicular unit vector, scaled by this pulse's own random arc offset.
    const cx = mx + (-dy / len) * p.arc;
    const cy = my + (dx / len) * p.arc;

    const x = (1 - t) * (1 - t) * p.from.x + 2 * (1 - t) * t * cx + t * t * p.to.x;
    const y = (1 - t) * (1 - t) * p.from.y + 2 * (1 - t) * t * cy + t * t * p.to.y;
    // Bezier tangent (derivative) — which way the "missile" is currently pointing.
    const tx = 2 * (1 - t) * (cx - p.from.x) + 2 * t * (p.to.x - cx);
    const ty = 2 * (1 - t) * (cy - p.from.y) + 2 * t * (p.to.y - cy);
    const angleDeg = (Math.atan2(ty, tx) * 180) / Math.PI;
    return { x, y, angleDeg };
  }

  useEffect(() => {
    const source = new EventSource("/api/live/stream");
    source.onopen = () => setConnected(true);
    source.onerror = () => setConnected(false);
    source.addEventListener("activity", (e) => {
      const event = JSON.parse((e as MessageEvent).data) as LiveEvent;
      setFeed((prev) => [event, ...prev].slice(0, 30));

      const eventPos = event.eventKey ? eventPositions[eventNodeKey(event.sourceKey, event.eventKey)] : undefined;
      const from = event.stage === "ingress" ? sourcePositions[event.sourceKey] : eventPos ?? hub;
      const to =
        event.stage === "ingress" ? eventPos ?? hub : event.targetKey ? targetPositions[event.targetKey] : null;
      if (!from || !to) return;

      const color = event.status === "failed" ? "#d6293e" : event.status === "success" ? "#0f8b3f" : "#4f46e5";
      spawnPulse(from, to, color);
    });
    return () => {
      source.close();
      if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);
    };
  }, [sourcePositions, eventPositions, targetPositions, hub]);

  return (
    <div>
      <div className="page-header">
        <h1>Live Activity</h1>
        <div className="live-header-controls">
          {loggedIn && simulator && (
            <label className="inline-checkbox simulator-toggle">
              <input
                type="checkbox"
                checked={simulator.running}
                disabled={simulatorBusy}
                onChange={toggleSimulator}
              />
              Demo event generator ({simulator.running ? "on" : "off"})
            </label>
          )}
          <span className={connected ? "badge badge-ok" : "badge badge-muted"}>
            {connected ? "● connected" : "connecting..."}
          </span>
        </div>
      </div>
      {simulatorError && <p className="error">{simulatorError}</p>}
      <p className="muted">
        Real-time Source &rarr; Source Event &rarr; RelayHub &rarr; Target traffic, pushed over SSE as it happens —
        no manual refresh. The Event column mirrors Subscriptions: one node per Source Event actually wired to a
        Target.
        {loggedIn && (
          <>
            {" "}
            Random flight events are generated by{" "}
            <a href="https://github.com/cleanbrain-developer/relayhub-demo-systems" target="_blank" rel="noreferrer">
              relayhub-demo-systems
            </a>
            , not by RelayHub itself — toggle it above.
          </>
        )}
      </p>

      <div className="card live-topology">
        <svg viewBox={`0 0 960 ${svgHeight}`} width="100%" height={svgHeight} preserveAspectRatio="xMidYMid meet">
          {sources.map((s) => {
            const pos = sourcePositions[s.key];
            if (!pos) return null;
            return (
              <rect
                key={s.key}
                x={pos.x - NODE_WIDTH_SOURCE / 2}
                y={pos.y - NODE_HEIGHT / 2}
                width={NODE_WIDTH_SOURCE}
                height={NODE_HEIGHT}
                rx={8}
                className="topology-node topology-node-source"
              />
            );
          })}
          {sources.map((s) => {
            const pos = sourcePositions[s.key];
            if (!pos) return null;
            return (
              <text key={s.key} x={pos.x} y={pos.y + 5} textAnchor="middle" className="topology-label">
                {s.key}
              </text>
            );
          })}

          {eventNodes.map((n) => {
            const sourcePos = sourcePositions[n.sourceKey];
            const pos = eventPositions[n.key];
            if (!sourcePos || !pos) return null;
            return (
              <line
                key={`edge-${n.key}`}
                x1={sourcePos.x + NODE_WIDTH_SOURCE / 2}
                y1={sourcePos.y}
                x2={pos.x - NODE_WIDTH_EVENT / 2}
                y2={pos.y}
                className="topology-edge"
              />
            );
          })}
          {eventNodes.map((n) => {
            const pos = eventPositions[n.key];
            if (!pos) return null;
            return (
              <line
                key={`edge-hub-${n.key}`}
                x1={pos.x + NODE_WIDTH_EVENT / 2}
                y1={pos.y}
                x2={hub.x - 66}
                y2={hub.y}
                className="topology-edge"
              />
            );
          })}
          {eventNodes.map((n) => {
            const pos = eventPositions[n.key];
            if (!pos) return null;
            return (
              <g key={n.key}>
                <rect
                  x={pos.x - NODE_WIDTH_EVENT / 2}
                  y={pos.y - NODE_HEIGHT / 2}
                  width={NODE_WIDTH_EVENT}
                  height={NODE_HEIGHT}
                  rx={8}
                  className="topology-node topology-node-event"
                />
                <text x={pos.x} y={pos.y + 5} textAnchor="middle" className="topology-label">
                  {n.eventKey}
                </text>
              </g>
            );
          })}

          {targets.map((t) => {
            const pos = targetPositions[t.key];
            if (!pos) return null;
            return (
              <g key={t.key}>
                <line x1={hub.x + 66} y1={hub.y} x2={pos.x - NODE_WIDTH_TARGET / 2} y2={pos.y} className="topology-edge" />
                <rect
                  x={pos.x - NODE_WIDTH_TARGET / 2}
                  y={pos.y - NODE_HEIGHT / 2}
                  width={NODE_WIDTH_TARGET}
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
            const { x, y, angleDeg } = pointOnArc(p, p.progress);
            const opacity = p.progress > 0.85 ? 1 - (p.progress - 0.85) / 0.15 : 1;
            // A short flame trail (warm colors near the nozzle, fading into the pulse's own
            // status color further back) reads as thrust far more convincingly than a plain dot.
            const trail = [0.06, 0.12, 0.19, 0.27].map((back) => pointOnArc(p, Math.max(p.progress - back, 0)));
            // A launch puff at t=0 and an impact burst at t=1 — the two "cute" flourishes that
            // turn a moving dot into something that reads as a tiny missile taking off/landing.
            const launchT = Math.min(p.progress / 0.18, 1);
            const impactT = p.progress > 0.82 ? (p.progress - 0.82) / 0.18 : 0;
            return (
              <g key={p.id}>
                {launchT < 1 && (
                  <circle
                    cx={p.from.x}
                    cy={p.from.y}
                    r={3 + launchT * 16}
                    fill="none"
                    stroke={p.color}
                    strokeWidth={2}
                    opacity={(1 - launchT) * 0.6}
                  />
                )}
                {impactT > 0 && (
                  <circle
                    cx={p.to.x}
                    cy={p.to.y}
                    r={3 + impactT * 20}
                    fill="none"
                    stroke={p.color}
                    strokeWidth={2}
                    opacity={(1 - impactT) * 0.7}
                  />
                )}
                <g opacity={opacity}>
                  {trail.map((t, i) => (
                    <circle
                      key={i}
                      cx={t.x}
                      cy={t.y}
                      r={PULSE_RADIUS * (0.65 - i * 0.13)}
                      fill={i < 2 ? "#ffb703" : p.color}
                      opacity={0.5 - i * 0.1}
                    />
                  ))}
                  <g className="topology-missile" style={{ color: p.color }} transform={`translate(${x} ${y}) rotate(${angleDeg})`}>
                    {/* fins */}
                    <path d="M -5,-6 L -13,-10 L -9,-3 Z" fill={p.color} opacity={0.95} />
                    <path d="M -5,6 L -13,10 L -9,3 Z" fill={p.color} opacity={0.95} />
                    {/* body */}
                    <ellipse cx={0} cy={0} rx={10} ry={5.5} fill={p.color} />
                    {/* nose cone */}
                    <path d="M 8,-4.5 L 15,0 L 8,4.5 Z" fill={p.color} />
                    {/* window */}
                    <circle cx={1.5} cy={0} r={2.2} fill="#fff" opacity={0.9} />
                  </g>
                </g>
              </g>
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
            <th>Event</th>
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
              <td>{e.eventKey ?? "-"}</td>
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
              <td colSpan={6} className="muted">
                Waiting for activity...
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
