import { useEffect, useState } from "react";
import { get } from "../api";
import { SourceEvent } from "../types";
import { FieldRegistryEditor } from "./FieldRegistryEditor";

interface Props {
  sourceKey: string;
  loggedIn: boolean;
}

/**
 * Lists a Source's registered events and, per event, its field registry (Spec 006 — see
 * FieldRegistryEditor) — always expanded, not behind a second collapse toggle, since burying the
 * add/edit/delete form two clicks deep was exactly why the maintainer kept re-asking whether this
 * feature existed at all (2026-09-12). There's no Source Event creation UI yet (out of scope here
 * — events are registered via the API/demo seeder today), so the event list itself is read-only;
 * only the fields nested under each event are editable.
 */
export function SourceEventFields({ sourceKey, loggedIn }: Props) {
  const [events, setEvents] = useState<SourceEvent[]>([]);

  useEffect(() => {
    get<SourceEvent[]>(`/api/sources/${sourceKey}/events`)
      .then(setEvents)
      .catch(() => setEvents([]));
  }, [sourceKey]);

  if (events.length === 0) {
    return <p className="muted">No Source Events registered for this Source yet.</p>;
  }

  return (
    <div className="source-event-fields">
      {events.map((ev) => (
        <div key={ev.key} className="source-event-row">
          <div className="source-event-row-header">
            <code>{ev.key}</code>
            <span className="muted"> &middot; {ev.operation}</span>
          </div>
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
