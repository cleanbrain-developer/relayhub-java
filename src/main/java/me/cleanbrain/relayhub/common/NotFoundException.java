package me.cleanbrain.relayhub.common;

/** Thrown when a registration lookup (Source, SourceEvent, Target, Subscription) fails. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
