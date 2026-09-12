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
 * FieldRegistryEditor). There's no Source Event creation UI yet (out of scope here — events are
 * registered via the API/demo seeder today), so this is read-only at the event level; only the
 * fields nested under each event are editable.
 */
export function SourceEventFields({ sourceKey, loggedIn }: Props) {
  const [events, setEvents] = useState<SourceEvent[]>([]);
  const [openEventKey, setOpenEventKey] = useState<string | null>(null);

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
          <button type="button" className="link-button" onClick={() => setOpenEventKey(openEventKey === ev.key ? null : ev.key)}>
            {openEventKey === ev.key ? "▾" : "▸"} <code>{ev.key}</code>
            <span className="muted"> &middot; {ev.operation} &middot; fields</span>
          </button>
          {openEventKey === ev.key && (
            <FieldRegistryEditor
              basePath={`/api/sources/${sourceKey}/events/${ev.key}/fields`}
              includeJsonPath
              loggedIn={loggedIn}
            />
          )}
        </div>
      ))}
    </div>
  );
}
