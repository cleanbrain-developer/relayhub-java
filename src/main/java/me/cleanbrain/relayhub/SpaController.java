package me.cleanbrain.relayhub;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards the React Router client-side routes to index.html so a hard refresh (or a direct
 * link) on e.g. /sources doesn't 404 — Spring Boot's static resource handler only knows about
 * actual files under src/main/resources/static/ (see frontend/vite.config.ts's build.outDir).
 * Listed explicitly (not a catch-all regex) so this can never shadow /api/**, /ingress/v1/**,
 * or /actuator/** — see security/SecurityConfig.java, which permits all of those separately.
 */
@Controller
public class SpaController {

    @GetMapping({"/", "/sources", "/targets", "/subscriptions", "/deliveries", "/login"})
    public String index() {
        return "forward:/index.html";
    }
}
