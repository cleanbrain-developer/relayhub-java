import { FormEvent, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { get, post } from "../api";
import { isLoggedIn } from "../auth";
import { Target } from "../types";
import { StatusBadge } from "../components/StatusBadge";
import { useToast } from "../toast";

const emptyForm = {
  key: "",
  name: "",
  description: "",
  baseUrl: "",
  authenticationType: "NONE" as const,
  authenticationConfig: "",
};

/**
 * List of Targets — browsing and creation only. Editing, Endpoints, Authentication, and the
 * dangerous actions all live on TargetDetailPage now (Stage 3, maintainer request 2026-09-30).
 */
export function TargetsPage() {
  const [targets, setTargets] = useState<Target[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [showInactive, setShowInactive] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const loggedIn = isLoggedIn();
  const { notify } = useToast();

  function reload() {
    get<Target[]>("/api/targets")
      .then(setTargets)
      .catch((err) => setError((err as Error).message))
      .finally(() => setLoading(false));
  }

  useEffect(reload, []);

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await post("/api/targets", form);
      setForm(emptyForm);
      setShowCreate(false);
      reload();
      notify(`Target "${form.key}" created.`);
    } catch (err) {
      setError((err as Error).message);
      notify((err as Error).message, "error");
    }
  }

  const visible = targets.filter((t) => showInactive || t.status === "ACTIVE");

  return (
    <div>
      <div className="page-header">
        <h1>Targets</h1>
        {loggedIn && (
          <button className="btn-primary" onClick={() => setShowCreate(!showCreate)}>
            {showCreate ? "Cancel" : "+ New Target"}
          </button>
        )}
      </div>
      {error && <p className="error">{error}</p>}

      <label className="inline-checkbox">
        <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
        Show deactivated (soft-deleted) Targets too
      </label>

      {showCreate && loggedIn && (
        <form onSubmit={handleCreate} className="card form-card">
          <div className="form-grid">
            <label>
              Key
              <input required value={form.key} onChange={(e) => setForm({ ...form, key: e.target.value })} />
            </label>
            <label>
              Name
              <input required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </label>
            <label className="form-wide">
              Description
              <input
                required
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
              />
            </label>
            <label className="form-wide">
              Base URL
              <input
                required
                value={form.baseUrl}
                onChange={(e) => setForm({ ...form, baseUrl: e.target.value })}
                placeholder="https://example.com"
              />
            </label>
          </div>
          <p className="muted" style={{ margin: 0 }}>
            Authentication can be configured after creation, from the Target's own page.
          </p>
          <button type="submit" className="btn-primary">
            Create Target
          </button>
        </form>
      )}

      <div className="card-list">
        {visible.map((t) => (
          <Link className="entity-row" to={`/targets/${t.key}`} key={t.key}>
            <div className="entity-row-main">
              <strong>{t.name}</strong>
              <div className="entity-row-sub">
                <code>{t.key}</code> &middot; <code>{t.baseUrl}</code>
              </div>
            </div>
            <StatusBadge value={t.status} />
          </Link>
        ))}
        {loading && <p className="muted">Loading Targets...</p>}
        {!loading && visible.length === 0 && (
          <p className="muted">{targets.length === 0 ? "No Targets yet." : "No active Targets — try “Show deactivated”."}</p>
        )}
      </div>
    </div>
  );
}
