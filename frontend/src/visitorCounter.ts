// Client for the shared cleanbrain-me-visitor-counter service. Deliberately
// separate from api.ts's get/post helpers, since those target this app's
// own same-origin `/api` backend, not an external cross-origin host.
const BASE_URL =
  import.meta.env.VITE_VISITOR_COUNTER_URL ?? "https://visitor-counter.cleanbrain.me";
const SERVICE_ID = "relayhub-java";
const SESSION_PING_KEY_PREFIX = "cleanbrain-visitor-pinged:";

function clientTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

// sessionStorage survives a refresh (it's only cleared when the tab
// closes), so a plain "have I pinged this session" flag would never fire
// again for a tab left open across local midnight -- keying it by the
// browser's own local calendar date instead makes the guard reset exactly
// when "today" does, without needing any date-comparison logic.
function todaysPingKey(): string {
  return SESSION_PING_KEY_PREFIX + new Date().toDateString();
}

export async function recordVisitOnce(): Promise<void> {
  const pingKey = todaysPingKey();
  try {
    if (sessionStorage.getItem(pingKey)) return;
    sessionStorage.setItem(pingKey, "1");
  } catch {
    // sessionStorage unavailable -- fall through and ping anyway.
  }

  try {
    await fetch(`${BASE_URL}/v1/visits`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ service: SERVICE_ID }),
    });
  } catch {
    // Counter being unreachable must never affect the page itself.
  }
}

export async function fetchTodayCount(): Promise<number | null> {
  try {
    const tz = encodeURIComponent(clientTimeZone());
    const res = await fetch(`${BASE_URL}/v1/visits/today?service=${SERVICE_ID}&tz=${tz}`);
    if (!res.ok) return null;
    const data = (await res.json()) as { count: number };
    return data.count;
  } catch {
    return null;
  }
}

export async function fetchAllTimeCount(): Promise<number | null> {
  try {
    const res = await fetch(`${BASE_URL}/v1/visits/all?service=${SERVICE_ID}`);
    if (!res.ok) return null;
    const data = (await res.json()) as { count: number };
    return data.count;
  } catch {
    return null;
  }
}
