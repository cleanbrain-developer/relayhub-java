package me.cleanbrain.relayhub.common;

import org.springframework.http.HttpMethod;

/**
 * A persistable stand-in for {@link HttpMethod}. Spring Framework 6's HttpMethod is no longer
 * a plain enum (it became an extensible value type), so it cannot be mapped with JPA's
 * {@code @Enumerated}. Ingress/target HTTP verbs are stored as this enum and converted to/from
 * {@link HttpMethod} only at the HTTP boundary (controllers, RestClient calls).
 */
public enum HttpVerb {
    GET, POST, PUT, PATCH, DELETE;

    public HttpMethod toSpring() {
        return HttpMethod.valueOf(name());
    }

    public static HttpVerb from(HttpMethod method) {
        return HttpVerb.valueOf(method.name());
    }
}
