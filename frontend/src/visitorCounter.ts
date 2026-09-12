// Client for the shared cleanbrain-me-visitor-counter service. Deliberately
// separate from api.ts's get/post helpers, since those target this app's
// own same-origin `/api` backend, not an external cross-origin host.
const BASE_URL =
  import.meta.env.VITE_VISITOR_COUNTER_URL ?? "https://visitor-counter.cleanbrain.me";
const SERVICE_ID = "relayhub-java";
const SESSION_PING_KEY = "cleanbrain-visitor-pinged";

function clientTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

export async function recordVisitOnce(): Promise<void> {
  try {
    if (sessionStorage.getItem(SESSION_PING_KEY)) return;
    sessionStorage.setItem(SESSION_PING_KEY, "1");
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
