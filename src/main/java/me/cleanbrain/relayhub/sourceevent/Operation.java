package me.cleanbrain.relayhub.sourceevent;

import me.cleanbrain.relayhub.common.HttpVerb;

/**
 * Data-change semantics for a Source Event, mapped to REST verbs per the RelayHub design:
 * CREATED=POST, REPLACED=PUT, PATCHED=PATCH, DELETED=DELETE.
 */
public enum Operation {
    CREATED(HttpVerb.POST),
    REPLACED(HttpVerb.PUT),
    PATCHED(HttpVerb.PATCH),
    DELETED(HttpVerb.DELETE);

    private final HttpVerb ingressMethod;

    Operation(HttpVerb ingressMethod) {
        this.ingressMethod = ingressMethod;
    }

    public HttpVerb ingressMethod() {
        return ingressMethod;
    }
}
