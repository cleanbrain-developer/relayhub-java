package me.cleanbrain.relayhub.ingress;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.ingress.dto.IngressResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Single dynamic endpoint for every registered Source Event's Ingress URL
 * (e.g. POST /ingress/v1/sap/customer-created). The URL itself carries no application logic —
 * routing to a Source/Event is entirely registration-driven, see IngressService.
 */
@RestController
@RequiredArgsConstructor
public class IngressController {

    private final IngressService ingressService;

    @RequestMapping(
            value = "/ingress/v1/**",
            method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE}
    )
    public ResponseEntity<IngressResponse> ingress(HttpServletRequest request,
                                                     @RequestBody(required = false) String rawBody) {
        String path = request.getRequestURI();
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        String body = rawBody != null ? rawBody : "{}";

        IngressService.IngressResult result = ingressService.handle(path, method, body, request);

        HttpStatus status = result.deduplicated() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status)
                .body(new IngressResponse(result.event().getId(), result.deliveryCount(), result.deduplicated()));
    }
}
