package me.cleanbrain.relayhub.delivery;

/**
 * Lifecycle of one (Event, Subscription) delivery unit. See specs/002-retry-dlq-replay/spec.md
 * and, for the Stage 2 non-blocking retry engine, db/migration/V12__delivery_retry_state.sql:
 * <pre>
 *   PENDING -&gt; PROCESSING -&gt; SUCCEEDED
 *   PROCESSING -&gt; RETRYING -&gt; PROCESSING (loop, via DeliveryRetryScheduler) -&gt; DEAD
 *   DEAD -&gt; REPLAYING -&gt; PROCESSING (replay re-enters the same retry loop, not just one attempt)
 * </pre>
 */
public enum DeliveryState {
    /** Just created; promoted to PROCESSING immediately (a real resting state only if the app
     *  crashed between insert and the first attempt — see DeliveryService.deliver). */
    PENDING,
    /** An HTTP attempt is in flight right now. */
    PROCESSING,
    SUCCEEDED,
    /** Between attempts, waiting on {@code nextAttemptAt}. DeliveryRetryScheduler picks these up
     *  once due — no thread or DB connection is held during the wait. */
    RETRYING,
    /** Retry attempts exhausted — the dead-letter state. Recoverable via replay. */
    DEAD,
    /** A replay attempt (manual or DlqAutoReplayScheduler) is in flight right now. */
    REPLAYING
}
