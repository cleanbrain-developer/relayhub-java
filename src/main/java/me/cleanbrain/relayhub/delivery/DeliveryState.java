package me.cleanbrain.relayhub.delivery;

/** Lifecycle of one (Event, Subscription) delivery unit. See specs/002-retry-dlq-replay/spec.md. */
public enum DeliveryState {
    /** Attempts are still possible within the current retry loop. */
    PENDING,
    SUCCEEDED,
    /** Retry attempts exhausted — the dead-letter state. Recoverable via replay. */
    DEAD
}
