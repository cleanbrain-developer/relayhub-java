import { authHeader } from "./auth";

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function handle<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new ApiError(res.status, body || res.statusText);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

/** Every GET in this app is public (see SecurityConfig.java) — no credentials attached. */
export function get<T>(path: string): Promise<T> {
  return fetch(path).then((res) => handle<T>(res));
}

/** For the handful of GETs that are admin-only (e.g. /api/auth/check, /api/simulator/status). */
export function getAuthed<T>(path: string): Promise<T> {
  return fetch(path, { headers: authHeader() }).then((res) => handle<T>(res));
}

function withBody(method: string, path: string, body?: unknown): Promise<Response> {
  return fetch(path, {
    method,
    headers: { "Content-Type": "application/json", ...authHeader() },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
}

/** POST/PUT/DELETE all require the admin credential — attached from sessionStorage if present. */
export function post<T>(path: string, body: unknown): Promise<T> {
  return withBody("POST", path, body).then((res) => handle<T>(res));
}

export function put<T>(path: string, body: unknown): Promise<T> {
  return withBody("PUT", path, body).then((res) => handle<T>(res));
}

export function del(path: string): Promise<void> {
  return withBody("DELETE", path).then((res) => handle<void>(res));
}
