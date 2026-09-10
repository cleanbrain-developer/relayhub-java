const STORAGE_KEY = "relayhub-console-credentials";

export interface Credentials {
  username: string;
  password: string;
}

/**
 * Session-scoped (not persisted across browser restarts) — the backend uses stateless HTTP
 * Basic (see SecurityConfig.java), so the SPA re-sends these on every protected request rather
 * than relying on a session cookie.
 */
export function saveCredentials(creds: Credentials): void {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(creds));
}

export function loadCredentials(): Credentials | null {
  const raw = sessionStorage.getItem(STORAGE_KEY);
  return raw ? (JSON.parse(raw) as Credentials) : null;
}

export function clearCredentials(): void {
  sessionStorage.removeItem(STORAGE_KEY);
}

export function isLoggedIn(): boolean {
  return loadCredentials() !== null;
}

export function authHeader(): Record<string, string> {
  const creds = loadCredentials();
  if (!creds) return {};
  return { Authorization: "Basic " + btoa(`${creds.username}:${creds.password}`) };
}
