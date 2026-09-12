import { DeliveryAttempt } from "../types";

function prettyJson(value: string | null): string {
  if (!value) return "-";
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

interface Props {
  attempt: DeliveryAttempt;
}

/**
 * The actual request sent to the Target and the actual response it sent back, for one Delivery
 * Attempt — not just its outcome (status/error), which is all DeliveriesPage's table and
 * LivePage's recent-activity feed showed before (maintainer request 2026-09-13). Shared between
 * both pages so "what did we actually send/receive" always looks the same regardless of where an
 * operator opened it from.
 */
export function AttemptDetail({ attempt }: Props) {
  return (
    <div className="attempt-detail">
      <div className="attempt-detail-section">
        <h4>Request</h4>
        <p className="muted">
          <code>{attempt.requestMethod ?? "?"}</code> <code>{attempt.requestUrl ?? "-"}</code>
        </p>
        <pre>{prettyJson(attempt.requestBody)}</pre>
      </div>
      <div className="attempt-detail-section">
        <h4>Response</h4>
        <p className="muted">
          HTTP status: <code>{attempt.httpStatus ?? "-"}</code>
        </p>
        {attempt.errorMessage && <p className="error">{attempt.errorMessage}</p>}
        <pre>{prettyJson(attempt.responseBody)}</pre>
      </div>
    </div>
  );
}
