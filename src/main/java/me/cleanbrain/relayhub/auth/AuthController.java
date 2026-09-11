package me.cleanbrain.relayhub.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exists solely so the admin console's login form can verify a password without side effects.
 * An earlier version had no such endpoint and "validated" a login by creating and immediately
 * deleting a throwaway Source — if that delete ever failed (or the request was interrupted), a
 * junk {@code __login-check-...} Source was left behind, ACTIVE, visible in the real Sources
 * list. Reaching this endpoint at all already proves the credentials in the Authorization header
 * were correct (see security/SecurityConfig.java: GET /api/auth/check requires ROLE_ADMIN, the
 * one GET that isn't public) — Spring Security rejects a bad Basic auth header with 401 before
 * this method body ever runs.
 */
@RestController
public class AuthController {

    @GetMapping("/api/auth/check")
    public void check() {
    }
}
