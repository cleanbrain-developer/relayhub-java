import { FormEvent, useEffect, useState } from "react";
import { del, get, post, put } from "../api";
import { Operation, SourceEvent } from "../types";
import { StatusBadge } from "./StatusBadge";
import { FieldRegistryEditor } from "./FieldRegistryEditor";

interface Props {
  sourceKey: string;
  loggedIn: boolean;
}

const OPERATIONS: Operation[] = ["CREATED", "REPLACED", "PATCHED", "DELETED"];

interface EventFormState {
  key: string;
  name: string;
  description: string;
  resourceType: string;
  operation: Operation;
  resourceIdPath: string;
  occurredAtPath: string;
  idempotencyKeyPath: string;
  idempotencyHeader: string;
  payloadSchema: string;
}

const emptyForm: EventFormState = {
  key: "",
  name: "",
  description: "",
  resourceType: "",
  operation: "CREATED",
  resourceIdPath: "",
  occurredAtPath: "",
  idempotencyKeyPath: "",
  idempotencyHeader: "",
  payloadSchema: "",
};

function toFormState(ev: SourceEvent): EventFormState {
  return {
    key: ev.key,
    name: ev.name,
    description: ev.description,
    resourceType: ev.resourceType,
    operation: ev.operation,
    resourceIdPath: ev.resourceIdPath,
    occurredAtPath: ev.occurredAtPath ?? "",
    idempotencyKeyPath: ev.idempotencyKeyPath ?? "",
    idempotencyHeader: ev.idempotencyHeader ?? "",
    payloadSchema: ev.payloadSchema ?? "",
  };
}

/**
 * Lists a Source's registered events, with full add/edit/deactivate/delete — not just a read-only
 * list of whatever the API/demo seeder happened to register, which is what this used to be
 * (maintainer feedback 2026-09-13: "Resource 데이터 Edit에 이벤트를 추가하는 기능이 없어" — Sources'
 * "Edit" only ever covered the Source record itself, never its Events). Each event's field
 * registry (Spec 006 — see FieldRegistryEditor) stays nested underneath, always expanded, same as
 * before.
 */
export function SourceEventFields({ sourceKey, loggedIn }: Props) {
  const [events, setEvents] = useState<SourceEvent[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [showInactive, setShowInactive] = useState(false);
  const [form, setForm] = useState<EventFormState>(emptyForm);
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [editForm, setEditForm] = useState<EventFormState>(emptyForm);

  function reload() {
    get<SourceEvent[]>(`/api/sources/${sourceKey}/events`)
      .then(setEvents)
      .catch((err) => setError((err as Error).message));
  }

  useEffect(reload, [sourceKey]);

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const { occurredAtPath, idempotencyKeyPath, idempotencyHeader, payloadSchema, ...rest } = form;
      await post(`/api/sources/${sourceKey}/events`, {
        ...rest,
        occurredAtPath: occurredAtPath || null,
        idempotencyKeyPath: idempotencyKeyPath || null,
        idempotencyHeader: idempotencyHeader || null,
        payloadSchema: payloadSchema || null,
      });
      setForm(emptyForm);
      setShowCreate(false);
      reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  function startEdit(ev: SourceEvent) {
    if (editingKey === ev.key) {
      setEditingKey(null);
      return;
    }
    setEditingKey(ev.key);
    setEditForm(toFormState(ev));
  }

  async function saveEdit(key: string) {
    setError(null);
    try {
      const { key: _key, occurredAtPath, idempotencyKeyPath, idempotencyHeader, payloadSchema, ...rest } = editForm;
      await put(`/api/sources/${sourceKey}/events/${key}`, {
        ...rest,
        occurredAtPath: occurredAtPath || null,
        idempotencyKeyPath: idempotencyKeyPath || null,
        idempotencyHeader: idempotencyHeader || null,
        payloadSchema: payloadSchema || null,
      });
      setEditingKey(null);
      reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  async function deactivate(key: string) {
    if (!confirm(`Deactivate Source Event "${key}"? Existing Events/Deliveries referencing it are kept.`)) return;
    setError(null);
    try {
      await del(`/api/sources/${sourceKey}/events/${key}`);
      reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  async function hardDelete(key: string) {
    if (
      !confirm(
        `Permanently delete Source Event "${key}"? This cannot be undone. Blocked if any Subscription still uses it.`
      )
    )
      return;
    setError(null);
    try {
      await del(`/api/sources/${sourceKey}/events/${key}?hard=true`);
      reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  function renderEventFields(state: EventFormState, setState: (s: EventFormState) => void, keyEditable: boolean) {
    return (
      <div className="form-grid">
        <label>
          Key
          <input
            required
            disabled={!keyEditable}
            value={state.key}
            onChange={(e) => setState({ ...state, key: e.target.value })}
          />
        </label>
        <label>
          Name
          <input required value={state.name} onChange={(e) => setState({ ...state, name: e.target.value })} />
        </label>
        <label className="form-wide">
          Description
          <input
            required
            value={state.description}
            onChange={(e) => setState({ ...state, description: e.target.value })}
          />
        </label>
        <label>
          Resource type
          <input
            required
            placeholder="e.g. flight"
            value={state.resourceType}
            onChange={(e) => setState({ ...state, resourceType: e.target.value })}
          />
        </label>
        <label>
          Operation
          <select
            value={state.operation}
            onChange={(e) => setState({ ...state, operation: e.target.value as Operation })}
          >
            {OPERATIONS.map((op) => (
              <option key={op}>{op}</option>
            ))}
          </select>
        </label>
        <label>
          Resource ID path
          <input
            required
            placeholder="$.id"
            value={state.resourceIdPath}
            onChange={(e) => setState({ ...state, resourceIdPath: e.target.value })}
          />
        </label>
        <label>
          Occurred-at path (optional)
          <input
            placeholder="$.occurredAt"
            value={state.occurredAtPath}
            onChange={(e) => setState({ ...state, occurredAtPath: e.target.value })}
          />
        </label>
        <label>
          Idempotency key path (optional)
          <input
            placeholder="$.eventId"
            value={state.idempotencyKeyPath}
            onChange={(e) => setState({ ...state, idempotencyKeyPath: e.target.value })}
          />
        </label>
        <label>
          Idempotency header (optional)
          <input
            placeholder="X-Idempotency-Key"
            value={state.idempotencyHeader}
            onChange={(e) => setState({ ...state, idempotencyHeader: e.target.value })}
          />
        </label>
        <label className="form-wide">
          Payload schema (optional, JSON Schema text)
          <textarea
            rows={3}
            value={state.payloadSchema}
            onChange={(e) => setState({ ...state, payloadSchema: e.target.value })}
          />
        </label>
      </div>
    );
  }

  const visible = events.filter((ev) => showInactive || ev.status === "ACTIVE");

  return (
    <div className="source-event-fields">
      {error && <p className="error">{error}</p>}
      <div className="field-registry-header">
        <label className="inline-checkbox">
          <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
          Show deactivated events
        </label>
        {loggedIn && (
          <button type="button" className="link-button" onClick={() => setShowCreate(!showCreate)}>
            {showCreate ? "Cancel" : "+ New Source Event"}
          </button>
        )}
      </div>

      {showCreate && loggedIn && (
        <form onSubmit={handleCreate} className="card form-card">
          {renderEventFields(form, setForm, true)}
          <button type="submit" className="btn-primary">
            Create Source Event
          </button>
        </form>
      )}

      {visible.length === 0 && (
        <p className="muted">
          {events.length === 0 ? "No Source Events registered for this Source yet." : "No active Source Events — try “Show deactivated events”."}
        </p>
      )}

      {visible.map((ev) => (
        <div key={ev.key} className="source-event-row">
          <div className="source-event-row-header">
            <div>
              <code>{ev.key}</code>
              <span className="muted">
                {" "}
                &middot; {ev.name} &middot; {ev.operation} ({ev.ingressMethod} <code>{ev.ingressPath}</code>)
              </span>
            </div>
            <div className="entity-card-actions">
              <StatusBadge value={ev.status} />
              {loggedIn && (
                <>
                  <button onClick={() => startEdit(ev)}>{editingKey === ev.key ? "Close" : "Edit"}</button>
                  {ev.status === "ACTIVE" && <button onClick={() => deactivate(ev.key)}>Deactivate</button>}
                  <button className="btn-danger" onClick={() => hardDelete(ev.key)}>
                    Delete permanently
                  </button>
                </>
              )}
            </div>
          </div>
          {editingKey === ev.key && (
            <div className="entity-card-edit">
              {renderEventFields(editForm, setEditForm, false)}
              <button type="button" className="btn-primary" onClick={() => saveEdit(ev.key)}>
                Save
              </button>
            </div>
          )}
          <FieldRegistryEditor
            basePath={`/api/sources/${sourceKey}/events/${ev.key}/fields`}
            includeJsonPath
            loggedIn={loggedIn}
          />
        </div>
      ))}
    </div>
  );
}
