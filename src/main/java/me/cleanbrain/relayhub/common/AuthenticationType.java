package me.cleanbrain.relayhub.common;

/**
 * Shared authentication strategy for Source and Target. Only {@link #NONE} and {@link #API_KEY}
 * are functionally wired today (authenticationConfig stays an opaque free-text string either way
 * -- this type doesn't decompose it). The rest are declared now so a later stage that actually
 * implements them doesn't need another migration just to widen the set of valid values.
 */
public enum AuthenticationType {
    NONE,
    API_KEY,
    HMAC,
    OAUTH2,
    BEARER_TOKEN,
    BASIC
}
