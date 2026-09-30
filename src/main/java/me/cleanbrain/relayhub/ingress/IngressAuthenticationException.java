package me.cleanbrain.relayhub.ingress;

/** A Source with {@code authenticationType=API_KEY} rejected this ingress request — missing or
 *  wrong {@code X-Api-Key} header. See {@link IngressService} and {@link me.cleanbrain.relayhub.common.ApiKeyAuth}. */
public class IngressAuthenticationException extends RuntimeException {

    public IngressAuthenticationException(String message) {
        super(message);
    }
}
