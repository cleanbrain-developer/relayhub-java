import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, get, post } from "./api";

/**
 * `handle()` used to throw the raw response body verbatim as the error message — every error
 * anywhere in the console showed the whole `ApiError` JSON blob (`{"timestamp":...,"message":"Source
 * key already registered: x",...}`) instead of just that message. Caught during the scale-out
 * readiness review's form-error placement work, 2026-10-06, by actually triggering a real
 * duplicate-key error against a live backend and looking at what rendered.
 */
describe("API error message extraction", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("extracts .message from a real ApiError JSON body instead of throwing the raw JSON", async () => {
    const body = JSON.stringify({
      timestamp: "2026-10-06T17:13:11.884707200Z",
      status: 400,
      error: "Bad Request",
      message: "Source key already registered: demo-flightstatus",
      path: "/api/sources",
    });
    vi.mocked(fetch).mockResolvedValue(new Response(body, { status: 400 }));

    await expect(get("/api/sources")).rejects.toMatchObject<Partial<ApiError>>({
      status: 400,
      message: "Source key already registered: demo-flightstatus",
    });
  });

  it("falls back to the raw body for a non-JSON error response", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response("Service Unavailable", { status: 503 }));

    await expect(post("/api/sources", {})).rejects.toMatchObject<Partial<ApiError>>({
      status: 503,
      message: "Service Unavailable",
    });
  });

  it("falls back to the status text for an empty error body", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response("", { status: 500, statusText: "Internal Server Error" }));

    await expect(get("/api/sources")).rejects.toMatchObject<Partial<ApiError>>({
      status: 500,
      message: "Internal Server Error",
    });
  });
});
