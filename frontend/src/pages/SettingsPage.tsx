import { RetryPolicyCard } from "../components/RetryPolicyCard";

/**
 * Centralizes what this console actually lets an operator configure (scale-out readiness review,
 * 2026-10-06 finding: no Settings page existed at all — the one real setting, Delivery retry
 * policy, sat buried inside the Deliveries list instead). Everything else that could be considered
 * a "setting" today only changes via config/environment variables, not this UI — listed below
 * rather than left for an operator to wonder about, same documentation discipline this project
 * already applies to deliberately-deferred features (see ADR-0005/ADR-0006).
 */
export function SettingsPage() {
  return (
    <div>
      <h1>Settings</h1>
      <RetryPolicyCard />

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <h2 style={{ marginTop: 0 }}>Not configurable from this console</h2>
        <p className="muted" style={{ marginBottom: 0 }}>
          These are set via deployment configuration (environment variables / Kubernetes Secrets), not this UI —
          changing them means a redeploy, by design, since they affect authentication or every Source/Target at
          once rather than one record:
        </p>
        <ul className="muted" style={{ marginTop: "0.5rem" }}>
          <li>The admin username/password (<code>relayhub.admin.username</code>/<code>relayhub.admin.password</code>)</li>
          <li>The allowed CORS origin for the separate <code>developer.cleanbrain.me</code> console embed</li>
          <li>
            The demo traffic generator's own tick interval and max concurrent flights (only on/off is toggleable
            from the Live page)
          </li>
        </ul>
      </div>
    </div>
  );
}
