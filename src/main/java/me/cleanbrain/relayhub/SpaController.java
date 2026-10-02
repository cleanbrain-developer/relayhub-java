package me.cleanbrain.relayhub;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards the React Router client-side routes to index.html so a hard refresh (or a direct
 * link) on e.g. /sources doesn't 404 — Spring Boot's static resource handler only knows about
 * actual files under src/main/resources/static/ (see frontend/vite.config.ts's build.outDir).
 * Listed explicitly (not a catch-all regex) so this can never shadow /api/**, /ingress/v1/**,
 * or /actuator/** — see security/SecurityConfig.java, which permits all of those separately.
 *
 * <p>The {@code /sources/*}, {@code /targets/*}, {@code /subscriptions/*} single-segment patterns
 * cover the List -> Detail routes Stage 3 of the integration-platform overhaul added
 * (2026-09-30) — a real bug found during the 2026-10-02 self-review's react-router-dom v7
 * upgrade verification, not caused by that upgrade: this list was never updated when those detail
 * routes were added, so a direct link or hard refresh on e.g. {@code /subscriptions/<uuid>} has
 * 404'd in production ever since, silently, because every actual test/verification of that stage
 * only ever navigated there via an in-app client-side &lt;Link&gt; click, which never hits the
 * server for that URL at all.
 */
@Controller
public class SpaController {

    @GetMapping({
            "/", "/sources", "/targets", "/subscriptions", "/deliveries", "/live", "/login",
            "/sources/*", "/targets/*", "/subscriptions/*"
    })
    public String index() {
        return "forward:/index.html";
    }
}
