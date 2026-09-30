import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { get, put, del } from "../api";
import { isLoggedIn } from "../auth";
import { Source } from "../types";
import { StatusBadge } from "../components/StatusBadge";
import { SourceEventFields } from "../components/SourceEventFields";
import { AuthenticationFields, AuthFormState } from "../components/AuthenticationFields";
import { Tabs } from "../components/Tabs";
import { useToast } from "../toast";

/**
 * Detail page for one Source — General (identity + authentication) and Source Events tabs (Stage
 * 3 of the integration-platform overhaul, maintainer request 2026-09-30: List -> Detail navigation
 * replacing the old always-expanded-inline-in-the-list-card pattern).
 */
export function SourceDetailPage() {
  const { key } = useParams<{ key: string }>();
  const navigate = useNavigate();
  const loggedIn = isLoggedIn();
  const { notify } = useToast();

  const [source, setSource] = useState<Source | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState<{ name: string; description: string } & AuthFormState>({
    name: "",
    description: "",
    authenticationType: "NONE",
    authenticationConfig: "",
  });

  function reload() {
    if (!key) return;
    get<Source>(`/api/sources/${key}`)
      .then((s) => {
        setSource(s);
        setForm({ name: s.name, description: s.description, authenticationType: s.authenticationType, authenticationConfig: "" });
      })
      .catch((err) => setError((err as Error).message));
  }

  useEffect(reload, [key]);

  async function save() {
    if (!key) return;
    setError(null);
    try {
      const updated = await put<Source>(`/api/sources/${key}`, form);
      setSource(updated);
      setForm({ ...form, authenticationConfig: "" });
      notify(`Source "${key}" updated.`);
    } catch (err) {
      setError((err as Error).message);
      notify((err as Error).message, "error");
    }
  }

  async function deactivate() {
    if (!key || !confirm(`Deactivate Source "${key}"? Existing Deliveries/history are kept.`)) return;
    try {
      await del(`/api/sources/${key}`);
      reload();
      notify(`Source "${key}" deactivated.`);
    } catch (err) {
      notify((err as Error).message, "error");
    }
  }

  async function hardDelete() {
    if (!key || !confirm(`Permanently delete Source "${key}"? This cannot be undone. Blocked if any Source Event still exists for it.`))
      return;
    try {
      await del(`/api/sources/${key}?hard=true`);
      notify(`Source "${key}" permanently deleted.`);
      navigate("/sources");
    } catch (err) {
      notify((err as Error).message, "error");
    }
  }

  if (error && !source) {
    return (
      <div>
        <Link to="/sources" className="back-link">
          &larr; Sources
        </Link>
        <p className="error">{error}</p>
      </div>
    );
  }
  if (!source) return <p className="muted">Loading Source...</p>;

  return (
    <div>
      <Link to="/sources" className="back-link">
        &larr; Sources
      </Link>
      <div className="detail-header">
        <div className="detail-header-title">
          <h1>{source.name}</h1>
          <StatusBadge value={source.status} />
        </div>
        {loggedIn && (
          <div className="detail-header-actions">
            {source.status === "ACTIVE" && <button onClick={deactivate}>Deactivate</button>}
            <button className="btn-danger" onClick={hardDelete}>
              Delete permanently
            </button>
          </div>
        )}
      </div>
      {error && <p className="error">{error}</p>}
      <p className="muted">
        <code>{source.key}</code> &middot; {source.description}
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
                <h2 style={{ marginTop: "1.25rem" }}>Authentication</h2>
                <AuthenticationFields
                  state={form}
                  onChange={(next) => setForm({ ...form, ...next })}
                  hasExistingSecret={source.authenticationType !== "NONE"}
                  direction="inbound"
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
            id: "events",
            label: "Source Events",
            content: (
              <>
                <p className="muted">
                  Fields each Source Event can supply — registered here so Subscriptions can map them via dropdown
                  instead of free-typed JSONPath.
                </p>
                <SourceEventFields sourceKey={source.key} loggedIn={loggedIn} />
              </>
            ),
          },
        ]}
      />
    </div>
  );
}
