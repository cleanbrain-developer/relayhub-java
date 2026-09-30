import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { get, put, del } from "../api";
import { isLoggedIn } from "../auth";
import { Target } from "../types";
import { StatusBadge } from "../components/StatusBadge";
import { TargetEndpoints } from "../components/TargetEndpoints";
import { AuthenticationFields, AuthFormState } from "../components/AuthenticationFields";
import { Tabs } from "../components/Tabs";
import { useToast } from "../toast";

/**
 * Detail page for one Target — General (identity + authentication) and Endpoints tabs. Mirrors
 * SourceDetailPage's structure (Stage 3, maintainer request 2026-09-30).
 */
export function TargetDetailPage() {
  const { key } = useParams<{ key: string }>();
  const navigate = useNavigate();
  const loggedIn = isLoggedIn();
  const { notify } = useToast();

  const [target, setTarget] = useState<Target | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState<{ name: string; description: string; baseUrl: string } & AuthFormState>({
    name: "",
    description: "",
    baseUrl: "",
    authenticationType: "NONE",
    authenticationConfig: "",
  });

  function reload() {
    if (!key) return;
    get<Target>(`/api/targets/${key}`)
      .then((t) => {
        setTarget(t);
        setForm({
          name: t.name,
          description: t.description,
          baseUrl: t.baseUrl,
          authenticationType: t.authenticationType,
          authenticationConfig: "",
        });
      })
      .catch((err) => setError((err as Error).message));
  }

  useEffect(reload, [key]);

  async function save() {
    if (!key) return;
    setError(null);
    try {
      const updated = await put<Target>(`/api/targets/${key}`, form);
      setTarget(updated);
      setForm({ ...form, authenticationConfig: "" });
      notify(`Target "${key}" updated.`);
    } catch (err) {
      setError((err as Error).message);
      notify((err as Error).message, "error");
    }
  }

  async function deactivate() {
    if (!key || !confirm(`Deactivate Target "${key}"? Existing Deliveries/history are kept.`)) return;
    try {
      await del(`/api/targets/${key}`);
      reload();
      notify(`Target "${key}" deactivated.`);
    } catch (err) {
      notify((err as Error).message, "error");
    }
  }

  async function hardDelete() {
    if (!key || !confirm(`Permanently delete Target "${key}"? This cannot be undone. Blocked if any Subscription still uses it.`))
      return;
    try {
      await del(`/api/targets/${key}?hard=true`);
      notify(`Target "${key}" permanently deleted.`);
      navigate("/targets");
    } catch (err) {
      notify((err as Error).message, "error");
    }
  }

  if (error && !target) {
    return (
      <div>
        <Link to="/targets" className="back-link">
          &larr; Targets
        </Link>
        <p className="error">{error}</p>
      </div>
    );
  }
  if (!target) return <p className="muted">Loading Target...</p>;

  return (
    <div>
      <Link to="/targets" className="back-link">
        &larr; Targets
      </Link>
      <div className="detail-header">
        <div className="detail-header-title">
          <h1>{target.name}</h1>
          <StatusBadge value={target.status} />
        </div>
        {loggedIn && (
          <div className="detail-header-actions">
            {target.status === "ACTIVE" && <button onClick={deactivate}>Deactivate</button>}
            <button className="btn-danger" onClick={hardDelete}>
              Delete permanently
            </button>
          </div>
        )}
      </div>
      {error && <p className="error">{error}</p>}
      <p className="muted">
        <code>{target.key}</code> &middot; <code>{target.baseUrl}</code>
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
                  <label className="form-wide">
                    Base URL
                    <input
                      disabled={!loggedIn}
                      value={form.baseUrl}
                      onChange={(e) => setForm({ ...form, baseUrl: e.target.value })}
                    />
                  </label>
                </div>
                <h2 style={{ marginTop: "1.25rem" }}>Authentication</h2>
                <AuthenticationFields
                  state={form}
                  onChange={(next) => setForm({ ...form, ...next })}
                  hasExistingSecret={target.authenticationType !== "NONE"}
                  direction="outbound"
                />
                {loggedIn && (
                  <button className="btn-primary" onClick={save} style={{ marginTop: "1rem" }}>
                    Save
                  </button>
                )}
              </div>
            ),
          },
          {
            id: "endpoints",
            label: "Endpoints",
            content: (
              <>
                <p className="muted">
                  Endpoints this Target exposes, each with its own request fields — registered here so Subscriptions
                  can pick a real API contract and map its fields via dropdown instead of free-typed method/path/field
                  names.
                </p>
                <TargetEndpoints targetKey={target.key} baseUrl={target.baseUrl} loggedIn={loggedIn} />
              </>
            ),
          },
        ]}
      />
    </div>
  );
}
