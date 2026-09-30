import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { del, get, put } from "../api";
import { isLoggedIn } from "../auth";
import { Subscription } from "../types";
import { StatusBadge } from "../components/StatusBadge";
import { MappingBuilder } from "../components/MappingBuilder";
import { Tabs } from "../components/Tabs";
import { useToast } from "../toast";

interface FormState {
  name: string;
  description: string;
  targetPayloadTemplate: string;
  filterExpression: string;
  maxAttempts: string;
  initialBackoffMs: string;
  maxBackoffMs: string;
  backoffMultiplier: string;
  jitter: boolean | null;
  timeoutMs: string;
}

function toFormState(s: Subscription): FormState {
  return {
    name: s.name,
    description: s.description,
    targetPayloadTemplate: s.targetPayloadTemplate,
    filterExpression: s.filterExpression ?? "",
    maxAttempts: s.maxAttempts !== null ? String(s.maxAttempts) : "",
    initialBackoffMs: s.initialBackoffMs !== null ? String(s.initialBackoffMs) : "",
    maxBackoffMs: s.maxBackoffMs !== null ? String(s.maxBackoffMs) : "",
    backoffMultiplier: s.backoffMultiplier !== null ? String(s.backoffMultiplier) : "",
    jitter: s.jitter,
    timeoutMs: s.timeoutMs !== null ? String(s.timeoutMs) : "",
  };
}

function toRequestBody(f: FormState) {
  return {
    name: f.name,
    description: f.description,
    targetPayloadTemplate: f.targetPayloadTemplate,
    filterExpression: f.filterExpression || null,
    maxAttempts: f.maxAttempts ? Number(f.maxAttempts) : null,
    initialBackoffMs: f.initialBackoffMs ? Number(f.initialBackoffMs) : null,
    maxBackoffMs: f.maxBackoffMs ? Number(f.maxBackoffMs) : null,
    backoffMultiplier: f.backoffMultiplier ? Number(f.backoffMultiplier) : null,
    jitter: f.jitter,
    timeoutMs: f.timeoutMs ? Number(f.timeoutMs) : null,
  };
}

/**
 * Detail page for one Subscription — General / Source / Target / Mapping / Filter / Delivery
 * Policy tabs (Stage 3 of the integration-platform overhaul, maintainer request 2026-09-30).
 * Source/Target/Mapping/Filter/Delivery-Policy all edit slices of the same
 * SubscriptionUpdateRequest, so there's one shared form state and one Save action in the header
 * rather than a separate save per tab (the pairing itself — which SourceEvent/TargetEndpoint —
 * isn't editable here at all; create a new Subscription to change that, same as the API).
 */
export function SubscriptionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const loggedIn = isLoggedIn();
  const { notify } = useToast();

  const [subscription, setSubscription] = useState<Subscription | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState<FormState | null>(null);

  function reload() {
    if (!id) return;
    get<Subscription>(`/api/subscriptions/${id}`)
      .then((s) => {
        setSubscription(s);
        setForm(toFormState(s));
      })
      .catch((err) => setError((err as Error).message));
  }

  useEffect(reload, [id]);

  async function save() {
    if (!id || !form) return;
    setError(null);
    try {
      const updated = await put<Subscription>(`/api/subscriptions/${id}`, toRequestBody(form));
      setSubscription(updated);
      setForm(toFormState(updated));
      notify("Subscription updated.");
    } catch (err) {
      setError((err as Error).message);
      notify((err as Error).message, "error");
    }
  }

  async function deactivate() {
    if (!id || !confirm("Deactivate this Subscription? Existing Delivery history is kept.")) return;
    try {
      await del(`/api/subscriptions/${id}`);
      reload();
      notify("Subscription deactivated.");
    } catch (err) {
      notify((err as Error).message, "error");
    }
  }

  async function hardDelete() {
    if (!id || !confirm("Permanently delete this Subscription? This cannot be undone.")) return;
    try {
      await del(`/api/subscriptions/${id}?hard=true`);
      notify("Subscription permanently deleted.");
      navigate("/subscriptions");
    } catch (err) {
      notify((err as Error).message, "error");
    }
  }

  if (error && !subscription) {
    return (
      <div>
        <Link to="/subscriptions" className="back-link">
          &larr; Subscriptions
        </Link>
        <p className="error">{error}</p>
      </div>
    );
  }
  if (!subscription || !form) return <p className="muted">Loading Subscription...</p>;

  return (
    <div>
      <Link to="/subscriptions" className="back-link">
        &larr; Subscriptions
      </Link>
      <div className="detail-header">
        <div className="detail-header-title">
          <h1>{subscription.name}</h1>
          <StatusBadge value={subscription.status} />
        </div>
        {loggedIn && (
          <div className="detail-header-actions">
            <button className="btn-primary" onClick={save}>
              Save
            </button>
            {subscription.status === "ACTIVE" && <button onClick={deactivate}>Deactivate</button>}
            <button className="btn-danger" onClick={hardDelete}>
              Delete permanently
            </button>
          </div>
        )}
      </div>
      {error && <p className="error">{error}</p>}
      <p className="muted">
        {subscription.sourceEventKey} &rarr; {subscription.targetKey}/{subscription.targetEndpointKey}
      </p>

      <Tabs
        tabs={[
          {
            id: "general",
            label: "General",
            content: (
              <div className="card form-card">
                <div className="form-grid">
                  <label>
                    Name
                    <input
                      disabled={!loggedIn}
                      value={form.name}
                      onChange={(e) => setForm({ ...form, name: e.target.value })}
                    />
                  </label>
                  <label className="form-wide">
                    Description
                    <input
                      disabled={!loggedIn}
                      value={form.description}
                      onChange={(e) => setForm({ ...form, description: e.target.value })}
                    />
                  </label>
                </div>
              </div>
            ),
          },
          {
            id: "source",
            label: "Source",
            content: (
              <div className="card">
                <p className="muted" style={{ marginTop: 0 }}>
                  The Source Event this Subscription listens to. Not editable here — create a new Subscription to
                  route a different Source Event.
                </p>
                <div className="form-grid">
                  <label>
                    Source
                    <span className="readonly-field">
                      <code>{subscription.sourceKey}</code>
                    </span>
                  </label>
                  <label>
                    Source Event
                    <span className="readonly-field">
                      <code>{subscription.sourceEventKey}</code>
                    </span>
                  </label>
                </div>
                <Link to={`/sources/${subscription.sourceKey}`} className="link-button">
                  View Source &rarr;
                </Link>
              </div>
            ),
          },
          {
            id: "target",
            label: "Target",
            content: (
              <div className="card">
                <p className="muted" style={{ marginTop: 0 }}>
                  The Target Endpoint this Subscription delivers to. Not editable here — create a new Subscription to
                  route to a different Target Endpoint.
                </p>
                <div className="form-grid">
                  <label>
                    Target
                    <span className="readonly-field">
                      <code>{subscription.targetKey}</code>
                    </span>
                  </label>
                  <label>
                    Target Endpoint
                    <span className="readonly-field">
                      <code>{subscription.targetEndpointKey}</code> ({subscription.targetMethod}{" "}
                      <code>{subscription.targetPath}</code>)
                    </span>
                  </label>
                </div>
                <Link to={`/targets/${subscription.targetKey}`} className="link-button">
                  View Target &rarr;
                </Link>
              </div>
            ),
          },
          {
            id: "mapping",
            label: "Mapping",
            content: (
              <div className="card">
                <MappingBuilder
                  value={form.targetPayloadTemplate}
                  onChange={(t) => setForm({ ...form, targetPayloadTemplate: t })}
                  sourceKey={subscription.sourceKey}
                  sourceEventKey={subscription.sourceEventKey}
                  targetKey={subscription.targetKey}
                  targetEndpointKey={subscription.targetEndpointKey}
                />
              </div>
            ),
          },
          {
            id: "filter",
            label: "Filter",
            content: (
              <div className="card">
                <p className="muted" style={{ marginTop: 0 }}>
                  Advanced: a boolean expression stored for future use but <strong>not yet evaluated</strong> at
                  delivery time — every active Subscription still delivers every matching Event regardless of what's
                  entered here (see docs/decisions/ADR-0005-subscription-filter-deferred.md).
                </p>
                <label className="form-wide">
                  Filter expression (optional)
                  <input
                    disabled={!loggedIn}
                    placeholder='e.g. status == "DELAYED"'
                    value={form.filterExpression}
                    onChange={(e) => setForm({ ...form, filterExpression: e.target.value })}
                  />
                </label>
              </div>
            ),
          },
          {
            id: "policy",
            label: "Delivery Policy",
            content: (
              <div className="card">
                <p className="muted" style={{ marginTop: 0 }}>
                  Every field below is optional — leave blank to use the deployment-wide default. Currently effective
                  max attempts for this Subscription: <strong>{subscription.effectiveMaxAttempts}</strong>.
                </p>
                <div className="form-grid">
                  <label>
                    Max attempts override
                    <input
                      disabled={!loggedIn}
                      type="number"
                      min={1}
                      max={10}
                      placeholder="uses the global default"
                      value={form.maxAttempts}
                      onChange={(e) => setForm({ ...form, maxAttempts: e.target.value })}
                    />
                  </label>
                  <label>
                    Initial backoff (ms)
                    <input
                      disabled={!loggedIn}
                      type="number"
                      min={0}
                      placeholder="default 200"
                      value={form.initialBackoffMs}
                      onChange={(e) => setForm({ ...form, initialBackoffMs: e.target.value })}
                    />
                  </label>
                  <label>
                    Max backoff (ms)
                    <input
                      disabled={!loggedIn}
                      type="number"
                      min={0}
                      placeholder="default 30000"
                      value={form.maxBackoffMs}
                      onChange={(e) => setForm({ ...form, maxBackoffMs: e.target.value })}
                    />
                  </label>
                  <label>
                    Backoff multiplier
                    <input
                      disabled={!loggedIn}
                      type="number"
                      min={1}
                      step="0.1"
                      placeholder="default 2.0"
                      value={form.backoffMultiplier}
                      onChange={(e) => setForm({ ...form, backoffMultiplier: e.target.value })}
                    />
                  </label>
                  <label>
                    Jitter
                    <select
                      disabled={!loggedIn}
                      value={form.jitter === null ? "" : String(form.jitter)}
                      onChange={(e) => setForm({ ...form, jitter: e.target.value === "" ? null : e.target.value === "true" })}
                    >
                      <option value="">Use default (off)</option>
                      <option value="true">On</option>
                      <option value="false">Off</option>
                    </select>
                  </label>
                  <label>
                    Request timeout (ms)
                    <input
                      disabled={!loggedIn}
                      type="number"
                      min={1}
                      placeholder="default 10000"
                      value={form.timeoutMs}
                      onChange={(e) => setForm({ ...form, timeoutMs: e.target.value })}
                    />
                  </label>
                </div>
              </div>
            ),
          },
        ]}
      />
    </div>
  );
}
