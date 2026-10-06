import { FormEvent, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { get, post } from "../api";
import { isLoggedIn } from "../auth";
import { Source } from "../types";
import { StatusBadge } from "../components/StatusBadge";
import { SearchBox } from "../components/SearchBox";
import { useToast } from "../toast";

const emptyForm = { key: "", name: "", description: "", authenticationType: "NONE" as const, authenticationConfig: "" };

/**
 * List of Sources — browsing and creation only. Editing, Source Events, Authentication, and the
 * dangerous actions (deactivate/hard-delete) all live on SourceDetailPage now (Stage 3, maintainer
 * request 2026-09-30: List -> Detail navigation replacing the old always-expanded-inline pattern).
 */
export function SourcesPage() {
  const [sources, setSources] = useState<Source[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [showInactive, setShowInactive] = useState(false);
  const [search, setSearch] = useState("");
  const [form, setForm] = useState(emptyForm);
  const loggedIn = isLoggedIn();
  const { notify } = useToast();

  function reload() {
    get<Source[]>("/api/sources")
      .then(setSources)
      .catch((err) => setError((err as Error).message))
      .finally(() => setLoading(false));
  }

  useEffect(reload, []);

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await post("/api/sources", form);
      setForm(emptyForm);
      setShowCreate(false);
      reload();
      notify(`Source "${form.key}" created.`);
    } catch (err) {
      setError((err as Error).message);
      notify((err as Error).message, "error");
    }
  }

  const query = search.trim().toLowerCase();
  const visible = sources
    .filter((s) => showInactive || s.status === "ACTIVE")
    .filter(
      (s) =>
        !query ||
        s.key.toLowerCase().includes(query) ||
        s.name.toLowerCase().includes(query) ||
        s.description.toLowerCase().includes(query)
    );

  return (
    <div>
      <div className="page-header">
        <h1>Sources</h1>
        {loggedIn && (
          <button className="btn-primary" onClick={() => setShowCreate(!showCreate)}>
            {showCreate ? "Cancel" : "+ New Source"}
          </button>
        )}
      </div>
      {error && <p className="error">{error}</p>}

      <div className="list-controls">
        <label className="inline-checkbox">
          <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
          Show deactivated (soft-deleted) Sources too
        </label>
        <SearchBox value={search} onChange={setSearch} placeholder="Search by key, name, description..." />
      </div>

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
          </div>
          <p className="muted" style={{ margin: 0 }}>
            Authentication can be configured after creation, from the Source's own page.
          </p>
          <button type="submit" className="btn-primary">
            Create Source
          </button>
        </form>
      )}

      <div className="card-list">
        {visible.map((s) => (
          <Link className="entity-row" to={`/sources/${s.key}`} key={s.key}>
            <div className="entity-row-main">
              <strong>{s.name}</strong>
              <div className="entity-row-sub">
                <code>{s.key}</code> &middot; {s.description}
              </div>
            </div>
            <StatusBadge value={s.status} />
          </Link>
        ))}
        {loading && <p className="muted">Loading Sources...</p>}
        {!loading && visible.length === 0 && (
          <p className="muted">
            {sources.length === 0
              ? "No Sources yet."
              : query
                ? `No Sources match "${search}".`
                : "No active Sources — try “Show deactivated”."}
          </p>
        )}
      </div>
    </div>
  );
}
