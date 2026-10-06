import { FormEvent, useEffect, useState } from "react";
import { get, put } from "../api";
import { isLoggedIn } from "../auth";
import { DeliverySettings } from "../types";
import { useToast } from "../toast";

/**
 * The global Delivery retry policy (max attempts before DEAD, DLQ auto-replay interval) — the one
 * setting this console has ever let an operator change from the UI. Moved here from DeliveriesPage
 * (scale-out readiness review, 2026-10-06 finding: there was no Settings page at all, so this sat
 * buried inside the Deliveries list instead of somewhere an operator would think to look for it).
 */
export function RetryPolicyCard() {
  const [maxAttempts, setMaxAttempts] = useState<number | null>(null);
  const [autoReplayIntervalMs, setAutoReplayIntervalMs] = useState<number | null>(null);
  const [editing, setEditing] = useState(false);
  const [maxAttemptsForm, setMaxAttemptsForm] = useState("");
  const [autoReplaySecondsForm, setAutoReplaySecondsForm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const loggedIn = isLoggedIn();
  const { notify } = useToast();

  function load() {
    get<DeliverySettings>("/api/delivery-settings")
      .then((s) => {
        setMaxAttempts(s.maxAttempts);
        setAutoReplayIntervalMs(s.autoReplayIntervalMs);
      })
      .catch(() => {});
  }

  useEffect(load, []);

  async function save(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const updated = await put<DeliverySettings>("/api/delivery-settings", {
        maxAttempts: Number(maxAttemptsForm),
        autoReplayIntervalMs: Number(autoReplaySecondsForm) * 1000,
      });
      setMaxAttempts(updated.maxAttempts);
      setAutoReplayIntervalMs(updated.autoReplayIntervalMs);
      setEditing(false);
      notify(
        `Retry policy updated — up to ${updated.maxAttempts} attempt(s) before DEAD, auto-replay every ${Math.round(updated.autoReplayIntervalMs / 1000)}s.`
      );
    } catch (err) {
      setError((err as Error).message);
      notify((err as Error).message, "error");
    }
  }

  return (
    <div className="card form-card">
      <h2 style={{ marginTop: 0 }}>Delivery retry policy</h2>
      {!editing ? (
        <div className="page-header" style={{ marginBottom: 0 }}>
          <span>
            Up to <strong>{maxAttempts ?? "?"}</strong> attempt(s) before a Delivery is marked <strong>DEAD</strong>{" "}
            (DLQ). Auto-replay sweeps the DLQ every{" "}
            <strong>{autoReplayIntervalMs !== null ? Math.round(autoReplayIntervalMs / 1000) : "?"}s</strong>.
          </span>
          {loggedIn && maxAttempts !== null && autoReplayIntervalMs !== null && (
            <button
              onClick={() => {
                setMaxAttemptsForm(String(maxAttempts));
                setAutoReplaySecondsForm(String(Math.round(autoReplayIntervalMs / 1000)));
                setError(null);
                setEditing(true);
              }}
            >
              Edit
            </button>
          )}
        </div>
      ) : (
        <form onSubmit={save} className="filter-row" style={{ alignItems: "flex-end" }}>
          <label>
            Max attempts before DEAD
            <input
              type="number"
              min={1}
              max={10}
              required
              value={maxAttemptsForm}
              onChange={(e) => setMaxAttemptsForm(e.target.value)}
            />
          </label>
          <label>
            Auto-replay every (seconds)
            <input
              type="number"
              min={5}
              max={3600}
              required
              value={autoReplaySecondsForm}
              onChange={(e) => setAutoReplaySecondsForm(e.target.value)}
            />
          </label>
          <button type="submit" className="btn-primary">
            Save
          </button>
          <button type="button" onClick={() => setEditing(false)}>
            Cancel
          </button>
          {error && <p className="error">{error}</p>}
        </form>
      )}
    </div>
  );
}
