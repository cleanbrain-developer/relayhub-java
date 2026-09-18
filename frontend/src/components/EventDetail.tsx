import { CanonicalEvent } from "../types";

interface Props {
  event: CanonicalEvent;
}

/**
 * The canonical Event RelayHub derived from the raw ingress payload for a Delivery — resourceId/
 * operation/occurredAt plus the actual payload JSON, not just the Delivery's own state/attempt
 * summary. Backend has had GET /api/events/{id} since Spec 001; there was no console UI to reach it
 * (completeness-audit finding, 2026-09-18).
 */
export function EventDetail({ event }: Props) {
  return (
    <div className="attempt-detail">
      <div className="attempt-detail-section">
        <h4>Event</h4>
        <p className="muted">
          <code>{event.resourceType}</code> <code>{event.resourceId}</code> &middot; {event.operation}
        </p>
        <p className="muted">
          Occurred: {event.occurredAt ? new Date(event.occurredAt).toLocaleString() : "-"} &middot; Received:{" "}
          {new Date(event.receivedAt).toLocaleString()}
        </p>
        {event.idempotencyKey && (
          <p className="muted">
            Idempotency key: <code>{event.idempotencyKey}</code>
          </p>
        )}
        <pre>{JSON.stringify(event.payload, null, 2)}</pre>
      </div>
    </div>
  );
}
