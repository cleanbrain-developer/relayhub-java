// Client for the shared cleanbrain-me-visitor-counter service. Deliberately
// separate from api.ts's get/post helpers, since those target this app's
// own same-origin `/api` backend, not an external cross-origin host.
const BASE_URL =
  import.meta.env.VITE_VISITOR_COUNTER_URL ?? "https://visitor-counter.cleanbrain.me";
const SERVICE_ID = "relayhub-java";

function clientTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

// No client-side "have I already pinged" guard: the backend already
// dedups correctly on its own (Today counts distinct ip_hash+user_agent
// within the day's range; All is a unique constraint on
// service+ip_hash+user_agent+day), so a plain ping on every page load is
// both simpler and more robust than trying to mirror that dedup logic
// client-side with sessionStorage -- which is per-tab state, invisible
// from the server, and silently permanent for the rest of the day if the
// one attempt it allowed happened to fail.
export async function recordVisit(): Promise<void> {
  try {
    await fetch(`${BASE_URL}/v1/visits`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ service: SERVICE_ID, tz: clientTimeZone() }),
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
