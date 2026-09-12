import { useEffect, useMemo, useRef, useState } from "react";
import { get, getAuthed, post } from "../api";
import { isLoggedIn } from "../auth";
import { DeliverySummary, Source, Subscription, Target } from "../types";

interface LiveEvent {
  stage: "ingress" | "delivery" | "dlq";
  sourceKey: string;
  eventKey: string | null;
  targetKey: string | null;
  status: "success" | "failed" | "dead" | null;
  /** True when this "delivery" traversal came from DeliveryService.replay (the manual Replay
   *  button, or DlqAutoReplayScheduler) rather than the original delivery attempt. */
  replay: boolean;
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
  /** 2+ points the pulse actually flies through in order — e.g. [Event, Hub, Target] for a
   *  delivery, so the flight visibly passes through the RelayHub node instead of arcing straight
   *  from Event to Target and skipping over it. */
  waypoints: Point[];
  /** One random perpendicular bezier-control-point offset per leg (waypoints.length - 1 of
   *  them), so consecutive missiles along the same edge don't overlap in a perfectly straight
   *  line, and each leg still gets its own gentle curve. */
  arcs: number[];
  /** Each leg's share of total flight time, proportional to its straight-line length so the
   *  missile doesn't visibly change speed at the waypoint in between (sums to 1). */
  legWeights: number[];
  color: string;
  start: number;
  /** "replay" draws a dashed halo around the missile — a retry (manual or auto) looks
   *  deliberately different from first-attempt traffic. */
  replay: boolean;
}

const NODE_X_SOURCE = 85;
const NODE_X_EVENT = 320;
const HUB_X = 555;
const NODE_X_TARGET = 855;
const NODE_WIDTH_SOURCE = 140;
const NODE_WIDTH_EVENT = 150;
const NODE_WIDTH_TARGET = 170;
const NODE_WIDTH_DLQ = 110;
const NODE_HEIGHT = 40;
const ROW_HEIGHT = 76;
const TOP_MARGIN = 50;
const PULSE_DURATION_MS = 950;
const PULSE_RADIUS = 10;
// Scales every local coordinate in the missile's SVG shape (fins/body/nose/window) — bumped up
// from the original 1x so the missile reads clearly even on a busy topology (maintainer feedback
// 2026-09-12: "더 화려해도 돼", asking for a bigger, showier Live page across the board).
const MISSILE_SCALE = 1.6;
const COLOR_INGRESS = "#4f46e5";
const COLOR_SUCCESS = "#0f8b3f";
const COLOR_FAILED = "#d6293e";
const DLQ_COLOR = "#7c2d12";
/** Warm gradient for the flame trail, brightest near the nozzle fading toward the pulse's own
 *  status color further back — more stops than a minimal trail needs, deliberately, for a fuller
 *  "flame" read instead of a handful of sparse dots. */
const TRAIL_COLORS = ["#fff3b0", "#ffd166", "#ffb703", "#ff8c42"];
const TRAIL_OFFSETS = [0.045, 0.09, 0.14, 0.19, 0.25, 0.32];

/** How showy each pulse's impact explosion is — DLQ landings and failures get the biggest "boom",
 *  a plain ingress arrival barely more than a puff. `colors` cycle across the radiating particles
 *  for a bit of sparkle instead of a monochrome burst. */
const EXPLOSION_PRESETS: Record<"ingress" | "success" | "failed" | "dlq", { count: number; distance: number; ring: number; colors: string[] }> = {
  ingress: { count: 6, distance: 18, ring: 16, colors: [COLOR_INGRESS, "#a5b4fc"] },
  success: { count: 10, distance: 32, ring: 28, colors: [COLOR_SUCCESS, "#ffd166", "#6ee7b7"] },
  failed: { count: 14, distance: 46, ring: 48, colors: [COLOR_FAILED, "#ff8c42", "#ffd166"] },
  dlq: { count: 18, distance: 56, ring: 60, colors: [DLQ_COLOR, "#8a8a8a", "#ff8c42", "#c2410c"] },
};

function explosionKind(color: string): keyof typeof EXPLOSION_PRESETS {
  if (color === DLQ_COLOR) return "dlq";
  if (color === COLOR_FAILED) return "failed";
  if (color === COLOR_SUCCESS) return "success";
  return "ingress";
}

/** Comic-style callout text that pops at the impact point — skipped for "ingress" (it lands on
 *  the Event node constantly and would drown everything else out), shown for the three stages
 *  that actually mean something happened. */
const IMPACT_TEXT: Partial<Record<keyof typeof EXPLOSION_PRESETS, string>> = {
  success: "HIT!",
  failed: "BOOM!",
  dlq: "DLQ!",
};

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
  const [deadCount, setDeadCount] = useState<number | null>(null);
  const [dlqNextRunAt, setDlqNextRunAt] = useState<number | null>(null);
  const [dlqSecondsLeft, setDlqSecondsLeft] = useState<number | null>(null);
  const loggedIn = isLoggedIn();
  const [renderedPulses, setRenderedPulses] = useState<(Pulse & { progress: number })[]>([]);
  const pulsesRef = useRef<Pulse[]>([]);
  const pulseId = useRef(0);
  const rafRef = useRef<number | null>(null);
  // When the pulse for a given key last started flying — used to delay a causally-later pulse
  // (a delivery following its ingress, a dlq drop following its delivery) until the earlier one
  // has actually finished, so two related pulses never visibly fly at once. Keyed separately per
  // stage transition since ingress->delivery and delivery->dlq have different "who's waiting on
  // whom" relationships.
  const lastIngressAt = useRef<Map<string, number>>(new Map());
  const lastDeliveryAt = useRef<Map<string, number>>(new Map());
  const summaryRefetchTimer = useRef<number | null>(null);
  // Brief screen-shake on the topology card when something dramatic lands (a failed delivery or
  // a DLQ drop) — timed to roughly coincide with the pulse's impact, not its spawn, via the same
  // readyDelay() scheduling used for the pulse itself.
  const [shaking, setShaking] = useState(false);
  const shakeTimer = useRef<number | null>(null);
  function triggerShake(delayMs: number) {
    window.setTimeout(() => {
      setShaking(true);
      if (shakeTimer.current !== null) window.clearTimeout(shakeTimer.current);
      shakeTimer.current = window.setTimeout(() => setShaking(false), 420);
    }, delayMs + PULSE_DURATION_MS * 0.8);
  }

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
    get<DeliverySummary>("/api/deliveries/summary")
      .then((s) => setDeadCount(s.dead))
      .catch((e) => console.error("Failed to load delivery summary", e));
    fetchDlqSchedule();
  }, []);

  function fetchDlqSchedule() {
    get<{ intervalMs: number; lastRunAt: string; nextRunAt: string }>("/api/dlq/schedule")
      .then((s) => setDlqNextRunAt(new Date(s.nextRunAt).getTime()))
      .catch((e) => console.error("Failed to load DLQ schedule", e));
  }

  // Re-fetch the DLQ count and next-sweep time shortly after anything that could change them (a
  // fresh dlq arrival, or a replay that just succeeded and left the queue) — debounced so a burst
  // of SSE events triggers one request pair, not one per event. Deliberately NOT called for a
  // replay that fails again: the delivery was already DEAD and stays DEAD, so nothing changed
  // (see DeliveryService.replay's Javadoc for the bug this used to cause).
  function scheduleDlqRefetch() {
    if (summaryRefetchTimer.current !== null) window.clearTimeout(summaryRefetchTimer.current);
    summaryRefetchTimer.current = window.setTimeout(() => {
      get<DeliverySummary>("/api/deliveries/summary")
        .then((s) => setDeadCount(s.dead))
        .catch(() => {});
      fetchDlqSchedule();
    }, 600);
  }

  // Ticks the visible countdown every second from dlqNextRunAt (server-authoritative, refreshed
  // above and once more shortly after each countdown hits zero — see below — so client/server
  // clock drift never accumulates across cycles). A ref (not state) tracks whether the
  // post-zero resync is already scheduled, so a fast-ticking interval doesn't queue it repeatedly.
  const resyncScheduledRef = useRef(false);
  useEffect(() => {
    const tick = window.setInterval(() => {
      if (dlqNextRunAt === null) return;
      const secondsLeft = Math.max(0, Math.ceil((dlqNextRunAt - Date.now()) / 1000));
      setDlqSecondsLeft(secondsLeft);
      if (secondsLeft === 0 && !resyncScheduledRef.current) {
        resyncScheduledRef.current = true;
        window.setTimeout(() => {
          fetchDlqSchedule();
          resyncScheduledRef.current = false;
        }, 1200);
      }
    }, 1000);
    return () => window.clearInterval(tick);
  }, [dlqNextRunAt]);

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
  // DLQ sits in its own row below every Source/Event/Target row, directly under the Hub — a
  // dead-lettered delivery is RelayHub's own terminal state, not something that belongs under any
  // particular Target's column.
  const dlqPos: Point = useMemo(() => ({ x: HUB_X, y: TOP_MARGIN + rowCount * ROW_HEIGHT }), [rowCount]);
  const svgHeight = TOP_MARGIN * 2 + rowCount * ROW_HEIGHT + 20;

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

  function spawnPulse(waypoints: Point[], color: string, replay = false) {
    // Random arc per leg (not always the same straight line) gives each "missile" its own flight
    // instead of a mechanical, identical repeat every time.
    const arcs = waypoints.slice(1).map(() => (Math.random() - 0.5) * 60);
    // Constant-speed feel across legs of very different lengths (e.g. a short Event->Hub hop
    // followed by a much longer Hub->Target one) — weight each leg's share of the total duration
    // by its own straight-line distance instead of splitting time evenly.
    const lengths = waypoints.slice(1).map((to, i) => Math.hypot(to.x - waypoints[i].x, to.y - waypoints[i].y) || 1);
    const totalLength = lengths.reduce((a, b) => a + b, 0);
    const legWeights = lengths.map((len) => len / totalLength);
    pulsesRef.current = [
      ...pulsesRef.current,
      { id: pulseId.current++, waypoints, arcs, legWeights, color, start: performance.now(), replay },
    ];
    if (rafRef.current === null) {
      rafRef.current = requestAnimationFrame(tick);
    }
  }

  /** Position + heading along one quadratic-bezier leg (from `a` to `b`, `arc`'s perpendicular
   *  offset, not a straight line) at local progress `t` (0..1). */
  function pointOnLeg(a: Point, b: Point, arc: number, t: number): { x: number; y: number; angleDeg: number } {
    const mx = (a.x + b.x) / 2;
    const my = (a.y + b.y) / 2;
    const dx = b.x - a.x;
    const dy = b.y - a.y;
    const len = Math.hypot(dx, dy) || 1;
    // Perpendicular unit vector, scaled by this leg's own random arc offset.
    const cx = mx + (-dy / len) * arc;
    const cy = my + (dx / len) * arc;

    const x = (1 - t) * (1 - t) * a.x + 2 * (1 - t) * t * cx + t * t * b.x;
    const y = (1 - t) * (1 - t) * a.y + 2 * (1 - t) * t * cy + t * t * b.y;
    // Bezier tangent (derivative) — which way the "missile" is currently pointing.
    const tx = 2 * (1 - t) * (cx - a.x) + 2 * t * (b.x - cx);
    const ty = 2 * (1 - t) * (cy - a.y) + 2 * t * (b.y - cy);
    const angleDeg = (Math.atan2(ty, tx) * 180) / Math.PI;
    return { x, y, angleDeg };
  }

  /** Position + heading along pulse `p`'s full (possibly multi-leg) flight path at overall
   *  progress `t` (0..1) — walks the leg whose time-share `t` falls into, so a delivery pulse
   *  with waypoints [Event, Hub, Target] visibly passes through the Hub node instead of arcing
   *  straight past it. */
  function pointOnPath(p: Pulse, t: number): { x: number; y: number; angleDeg: number } {
    let acc = 0;
    for (let i = 0; i < p.legWeights.length; i++) {
      const w = p.legWeights[i];
      const isLast = i === p.legWeights.length - 1;
      if (t <= acc + w || isLast) {
        const localT = w > 0 ? Math.min(Math.max((t - acc) / w, 0), 1) : 1;
        return pointOnLeg(p.waypoints[i], p.waypoints[i + 1], p.arcs[i], localT);
      }
      acc += w;
    }
    return pointOnLeg(p.waypoints[0], p.waypoints[1], p.arcs[0], 0);
  }

  /** Delay (ms) before `key` is free, per `map`'s last-recorded "busy until" timestamp — 0 if
   *  `key` isn't tracked yet or is already free. Every pulse occupies its key for exactly
   *  PULSE_DURATION_MS regardless of how many legs it flies (leg weights only split *within*
   *  that fixed budget), so "busy until" is simply spawn-time + PULSE_DURATION_MS. */
  function readyDelay(map: Map<string, number>, key: string, now: number): number {
    const busyUntil = map.get(key);
    return busyUntil !== undefined ? Math.max(0, busyUntil - now) : 0;
  }

  useEffect(() => {
    const source = new EventSource("/api/live/stream");
    source.onopen = () => setConnected(true);
    source.onerror = () => setConnected(false);
    source.addEventListener("activity", (e) => {
      const event = JSON.parse((e as MessageEvent).data) as LiveEvent;
      setFeed((prev) => [event, ...prev].slice(0, 30));

      const eventPos = event.eventKey ? eventPositions[eventNodeKey(event.sourceKey, event.eventKey)] : undefined;
      const color = event.status === "failed" ? COLOR_FAILED : event.status === "success" ? COLOR_SUCCESS : COLOR_INGRESS;
      const ingressKey = event.eventKey ? eventNodeKey(event.sourceKey, event.eventKey) : event.sourceKey;
      const now = performance.now();

      if (event.stage === "ingress") {
        const from = sourcePositions[event.sourceKey];
        if (!from) return;
        // Waits for a still-in-flight ingress on the same Source Event to land first, so two
        // ingresses for the same event never visibly overlap.
        const delay = readyDelay(lastIngressAt.current, ingressKey, now);
        lastIngressAt.current.set(ingressKey, now + delay + PULSE_DURATION_MS);
        const spawn = () => spawnPulse([from, eventPos ?? hub], color);
        delay > 0 ? window.setTimeout(spawn, delay) : spawn();
      } else if (event.stage === "delivery") {
        const to = event.targetKey ? targetPositions[event.targetKey] : null;
        if (!to) return;
        const deliveryKey = `${ingressKey}:${event.targetKey}`;
        // Waits for its own ingress to actually land, and for any still-in-flight delivery to the
        // same Target, before departing — so a delivery never visibly departs before the ingress
        // that caused it has arrived.
        const delay = Math.max(
          readyDelay(lastIngressAt.current, ingressKey, now),
          readyDelay(lastDeliveryAt.current, deliveryKey, now)
        );
        lastDeliveryAt.current.set(deliveryKey, now + delay + PULSE_DURATION_MS);
        // Routed explicitly through the Hub node (not a direct Event->Target arc) so the flight
        // visibly passes through RelayHub instead of appearing to skip over it.
        const spawn = () => spawnPulse([eventPos ?? hub, hub, to], color, event.replay);
        delay > 0 ? window.setTimeout(spawn, delay) : spawn();
        if (event.status === "failed") triggerShake(delay);
        // Only a replay that *succeeded* actually changed the DLQ count (it just left the
        // queue) — a replay that failed again was already DEAD and stays DEAD.
        if (event.replay && event.status === "success") scheduleDlqRefetch();
      } else if (event.stage === "dlq") {
        const deliveryKey = `${ingressKey}:${event.targetKey}`;
        // Waits for the failed delivery attempt that caused this to actually land at its Target
        // before departing for the DLQ.
        const delay = readyDelay(lastDeliveryAt.current, deliveryKey, now);
        lastDeliveryAt.current.set(deliveryKey, now + delay + PULSE_DURATION_MS);
        const spawn = () => spawnPulse([hub, dlqPos], DLQ_COLOR);
        delay > 0 ? window.setTimeout(spawn, delay) : spawn();
        triggerShake(delay);
        scheduleDlqRefetch();
      }
    });
    return () => {
      source.close();
      if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);
    };
  }, [sourcePositions, eventPositions, targetPositions, hub, dlqPos]);

  return (
    <div>
      <div className="page-header">
        <h1 className="live-title">Live Activity</h1>
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
        Target. A delivery that exhausts its retries drops into the DLQ node below RelayHub; a background job
        automatically retries the oldest DLQ items every 30s (retries carry a dashed halo).
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

      <div className={`card live-topology${shaking ? " topology-shake" : ""}`}>
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

          <rect
            x={hub.x - 60}
            y={hub.y - 24}
            width={120}
            height={48}
            rx={12}
            className="topology-hub"
            style={{ animationDuration: `${Math.max(0.5, 2.2 - renderedPulses.length * 0.18)}s` }}
          />
          <text x={hub.x} y={hub.y + 5} textAnchor="middle" className="topology-hub-label">
            RelayHub
          </text>

          <line x1={hub.x} y1={hub.y + 24} x2={dlqPos.x} y2={dlqPos.y - NODE_HEIGHT / 2} className="topology-edge topology-edge-dlq" />
          <rect
            x={dlqPos.x - NODE_WIDTH_DLQ / 2}
            y={dlqPos.y - NODE_HEIGHT / 2}
            width={NODE_WIDTH_DLQ}
            height={NODE_HEIGHT}
            rx={8}
            className="topology-node topology-node-dlq"
          />
          <text x={dlqPos.x} y={dlqPos.y + 5} textAnchor="middle" className="topology-label topology-label-dlq">
            DLQ{deadCount !== null ? ` (${deadCount})` : ""}
          </text>
          {dlqSecondsLeft !== null && (
            <text x={dlqPos.x} y={dlqPos.y + NODE_HEIGHT / 2 + 16} textAnchor="middle" className="topology-label-dlq-timer">
              auto-replay in {dlqSecondsLeft}s
            </text>
          )}

          {renderedPulses.map((p) => {
            const { x, y, angleDeg } = pointOnPath(p, p.progress);
            const opacity = p.progress > 0.85 ? 1 - (p.progress - 0.85) / 0.15 : 1;
            // A long, multi-stop flame trail (bright near the nozzle, cooling through orange into
            // the pulse's own status color further back) reads as real thrust, not a handful of
            // sparse dots.
            const trail = TRAIL_OFFSETS.map((back) => pointOnPath(p, Math.max(p.progress - back, 0)));
            // A launch puff at t=0 and an impact burst at t=1 — the two flourishes that turn a
            // moving dot into something that reads as a tiny missile taking off/landing.
            const launchT = Math.min(p.progress / 0.18, 1);
            const impactT = p.progress > 0.8 ? (p.progress - 0.8) / 0.2 : 0;
            const start = p.waypoints[0];
            const end = p.waypoints[p.waypoints.length - 1];
            const explosion = EXPLOSION_PRESETS[explosionKind(p.color)];
            const s = MISSILE_SCALE;
            return (
              <g key={p.id}>
                {launchT < 1 && (
                  <>
                    <circle
                      cx={start.x}
                      cy={start.y}
                      r={4 + launchT * 22}
                      fill="none"
                      stroke={p.color}
                      strokeWidth={2.5}
                      opacity={(1 - launchT) * 0.65}
                    />
                    <circle cx={start.x} cy={start.y} r={Math.max(0, 10 - launchT * 10)} fill="#fff" opacity={(1 - launchT) * 0.6} />
                  </>
                )}
                {impactT > 0 && (
                  <g>
                    {/* two staggered shockwave rings, the outer trailing the inner — a "double
                        boom" instead of one clean circle */}
                    <circle
                      cx={end.x}
                      cy={end.y}
                      r={5 + impactT * explosion.ring}
                      fill="none"
                      stroke={p.color}
                      strokeWidth={3}
                      opacity={(1 - impactT) * 0.85}
                    />
                    <circle
                      cx={end.x}
                      cy={end.y}
                      r={Math.max(0, 5 + (impactT - 0.2) * explosion.ring * 0.75)}
                      fill="none"
                      stroke={explosion.colors[explosion.colors.length - 1]}
                      strokeWidth={2}
                      opacity={Math.max(0, (1 - impactT) * 0.6 - 0.1)}
                    />
                    {/* bright flash at the moment of impact */}
                    <circle cx={end.x} cy={end.y} r={Math.max(0, 14 - impactT * 14)} fill="#fff" opacity={(1 - impactT) * 0.9} />
                    {/* comic-book callout — the last flashy flourish: a bold word that pops and
                        drifts up out of the explosion, not just particles */}
                    {IMPACT_TEXT[explosionKind(p.color)] && impactT < 0.75 && (
                      <text
                        x={end.x}
                        y={end.y - 22 - impactT * 26}
                        textAnchor="middle"
                        className="topology-impact-text"
                        style={{
                          fill: p.color,
                          opacity: impactT < 0.15 ? impactT / 0.15 : 1 - (impactT - 0.15) / 0.6,
                          transform: `scale(${0.5 + Math.min(impactT / 0.2, 1) * 0.8})`,
                          transformOrigin: `${end.x}px ${end.y - 22}px`,
                        }}
                      >
                        {IMPACT_TEXT[explosionKind(p.color)]}
                      </text>
                    )}
                    {/* radiating "펑펑" debris — count/reach/palette scale up from a plain ingress
                        arrival through a DLQ drop, the most dramatic landing of the three. Sizes
                        alternate big "chunks" and small "sparks" instead of uniform dots. */}
                    {Array.from({ length: explosion.count }).map((_, i) => {
                      const angle = (i / explosion.count) * Math.PI * 2 + p.id * 0.37;
                      const jitter = ((p.id * 13 + i * 7) % 10) / 10;
                      const dist = impactT * explosion.distance * (0.7 + jitter * 0.5);
                      const px = end.x + Math.cos(angle) * dist;
                      const py = end.y + Math.sin(angle) * dist;
                      const chunk = i % 3 === 0;
                      return (
                        <circle
                          key={i}
                          cx={px}
                          cy={py}
                          r={Math.max(0, (chunk ? 4.2 : 2.4) * (1 - impactT))}
                          fill={explosion.colors[i % explosion.colors.length]}
                          opacity={1 - impactT}
                        />
                      );
                    })}
                  </g>
                )}
                <g opacity={opacity}>
                  {/* comet-tail ribbon — thin connecting strokes between consecutive trail points
                      (and the missile itself) underneath the trail dots, so the flame reads as one
                      continuous streak instead of a string of separate blobs */}
                  {[{ x, y }, ...trail].map((point, i, arr) =>
                    i === 0 ? null : (
                      <line
                        key={`ribbon-${i}`}
                        x1={arr[i - 1].x}
                        y1={arr[i - 1].y}
                        x2={point.x}
                        y2={point.y}
                        stroke={i <= TRAIL_COLORS.length ? TRAIL_COLORS[i - 1] : p.color}
                        strokeWidth={PULSE_RADIUS * (0.9 - i * 0.12)}
                        strokeLinecap="round"
                        opacity={0.4 - i * 0.05}
                      />
                    )
                  )}
                  {trail.map((t, i) => (
                    <circle
                      key={i}
                      cx={t.x}
                      cy={t.y}
                      r={PULSE_RADIUS * (0.78 - i * 0.1)}
                      fill={i < TRAIL_COLORS.length ? TRAIL_COLORS[i] : p.color}
                      opacity={0.6 - i * 0.08}
                    />
                  ))}
                  <g className="topology-missile" style={{ color: p.color }} transform={`translate(${x} ${y}) rotate(${angleDeg})`}>
                    {/* a retry (manual replay or DlqAutoReplayScheduler) gets a dashed halo, so it
                        reads as deliberately different from first-attempt traffic */}
                    {p.replay && (
                      <circle cx={0} cy={0} r={13 * s} fill="none" stroke={p.color} strokeWidth={1.5} strokeDasharray="3 2" />
                    )}
                    {/* fins */}
                    <path
                      d={`M ${-5 * s},${-6 * s} L ${-13 * s},${-10 * s} L ${-9 * s},${-3 * s} Z`}
                      fill={p.color}
                      opacity={0.95}
                    />
                    <path
                      d={`M ${-5 * s},${6 * s} L ${-13 * s},${10 * s} L ${-9 * s},${3 * s} Z`}
                      fill={p.color}
                      opacity={0.95}
                    />
                    {/* body, with a lighter highlight stripe for a bit of shading */}
                    <ellipse cx={0} cy={0} rx={10 * s} ry={5.5 * s} fill={p.color} />
                    <ellipse cx={-1 * s} cy={-1.6 * s} rx={7 * s} ry={1.6 * s} fill="#fff" opacity={0.25} />
                    {/* nose cone */}
                    <path d={`M ${8 * s},${-4.5 * s} L ${15 * s},0 L ${8 * s},${4.5 * s} Z`} fill={p.color} />
                    {/* window */}
                    <circle cx={1.5 * s} cy={0} r={2.2 * s} fill="#fff" opacity={0.9} />
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
              <td>
                {e.stage}
                {e.replay && <span className="badge badge-warn badge-inline">replay</span>}
              </td>
              <td>{e.sourceKey}</td>
              <td>{e.eventKey ?? "-"}</td>
              <td>{e.targetKey ?? "-"}</td>
              <td>
                {e.status && (
                  <span
                    className={`badge ${
                      e.status === "success" ? "badge-ok" : e.status === "dead" ? "badge-muted" : "badge-danger"
                    }`}
                  >
                    {e.status}
                  </span>
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
