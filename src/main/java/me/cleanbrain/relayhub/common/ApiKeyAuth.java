package me.cleanbrain.relayhub.common;

/**
 * The one HTTP header name {@code AuthenticationType.API_KEY} actually means, on both sides of
 * RelayHub (maintainer request 2026-09-30, "API_KEY 인증 실제 적용"): a Source with
 * {@code authenticationType=API_KEY} requires this header on every inbound ingress request (see
 * {@code IngressService}), and a Target with {@code authenticationType=API_KEY} gets this header
 * attached on every outbound delivery attempt (see {@code DeliveryService}). Centralized here
 * (rather than a literal string in each) so the convention is documented exactly once and the two
 * sides can never silently drift apart.
 */
public final class ApiKeyAuth {

    public static final String HEADER_NAME = "X-Api-Key";

    private ApiKeyAuth() {
    }
}
