package me.cleanbrain.relayhub.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Single fixed admin account (no user table — see specs/005-admin-console/spec.md), guarding only
 * the write endpoints under /api/**. Everything else (every GET, /ingress/v1/** — which is
 * Source-system-facing, not admin-facing — /actuator/**, and the SPA's static assets) stays
 * unauthenticated, unchanged from before this spec. HTTP Basic + stateless: the React SPA sends
 * credentials per-request rather than relying on a session cookie, so CSRF protection (meant for
 * cookie-authenticated browser forms) is not applicable here.
 */
@Configuration
public class SecurityConfig {

    @Value("${relayhub.admin.username}")
    private String adminUsername;

    @Value("${relayhub.admin.password}")
    private String adminPassword;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername(adminUsername)
                        .password(passwordEncoder.encode(adminPassword))
                        .roles("ADMIN")
                        .build());
    }

    /**
     * developer.cleanbrain.me reads this service's public, read-only observability endpoints
     * directly from the browser, including the real-time SSE activity stream that drives a ported
     * version of this app's own Live topology animation — see that repo's ADR-0004. Scoped to GET
     * only and to exactly that one origin; it grants no write access and no broader origin
     * allowlist. These paths were already unauthenticated for same-origin requests (see the
     * GET-is-public rule below) — this bean only lets a browser on a different origin read the
     * response body (or, for /api/live/stream, the response stream) too.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("https://developer.cleanbrain.me"));
        configuration.setAllowedMethods(List.of("GET"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/deliveries/**", configuration);
        source.registerCorsConfiguration("/api/targets/**", configuration);
        source.registerCorsConfiguration("/api/metrics/**", configuration);
        source.registerCorsConfiguration("/actuator/**", configuration);
        // Added for the Live topology port (developer.cleanbrain.me): the topology diagram needs
        // Sources/Targets/Subscriptions to lay out its nodes, the DLQ auto-replay countdown, and
        // the SSE stream itself to animate in real time.
        source.registerCorsConfiguration("/api/sources/**", configuration);
        source.registerCorsConfiguration("/api/subscriptions/**", configuration);
        source.registerCorsConfiguration("/api/dlq/**", configuration);
        source.registerCorsConfiguration("/api/live/**", configuration);
        // Added for the Live activity drill-down on developer.cleanbrain.me: clicking a "delivery"
        // row in the Recent Activity table looks up that specific attempt's request/response/error
        // detail by id (GET /api/delivery-attempts/{id}) -- the parent delivery's retry history via
        // GET /api/deliveries/{deliveryId}/attempts was already covered by /api/deliveries/** above.
        // Both are public now that the admin gate on Attempt data was reopened (see the
        // authorizeHttpRequests comment below) -- this bean only extends that to cross-origin reads.
        source.registerCorsConfiguration("/api/delivery-attempts/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Evaluated in order, first match wins — this one exception must come
                        // before the blanket "every GET is public" rule below, or it would never
                        // be reached. See auth/AuthController.java: the login form's only way to
                        // validate a password without side-effecting real data.
                        .requestMatchers(HttpMethod.GET, "/api/auth/check").hasRole("ADMIN")
                        // Same reason: pausing/resuming demo traffic is an admin control action,
                        // not read-only observability data like /api/metrics/**, so its GET status
                        // check must also be excepted from the blanket "every GET is public" rule.
                        .requestMatchers(HttpMethod.GET, "/api/simulator/**").hasRole("ADMIN")
                        // An Attempt carries the actual request/response bodies exchanged with a
                        // Target — real payload content, not just outcome. This was gated behind
                        // admin auth for that reason (self-review finding, 2026-09-17), then reopened
                        // as public (2026-09-29, maintainer decision): every Source/Target attached to
                        // this deployment is a demo system under relayhub-demo-systems, generating
                        // synthetic traffic only — there is no real target integration, and none is
                        // planned, so there is no real payload to protect. developer.cleanbrain.me's
                        // Live activity drill-down (see that repo's ADR-0004) reads this directly.
                        // Revisit if a real (non-demo) Target is ever connected.
                        // The canonical Event carries the raw ingress payload verbatim, not just
                        // metadata (completeness-audit finding, 2026-09-18 — this endpoint existed
                        // but was never gated when the attempts endpoints above were first locked
                        // down). Left gated even after the Attempt endpoints were reopened above:
                        // unlike Attempt (Target-bound, demo-only traffic confirmed), no equivalent
                        // "this data source is permanently synthetic" review has been done for Event
                        // ingress payloads specifically — revisit deliberately if/when it is.
                        .requestMatchers(HttpMethod.GET, "/api/events/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .requestMatchers("/ingress/v1/**").permitAll()
                        // GET-scoped, not a bare "/actuator/**" permitAll (self-review finding,
                        // 2026-10-02): only health/prometheus/metrics are exposed today
                        // (management.endpoints.web.exposure.include), all GET-only reads, so this
                        // was never reachable in practice — but explicit here rather than relying
                        // on it. If a sibling service copies this config and later widens actuator
                        // exposure to something with a write operation (e.g. /actuator/loggers),
                        // this rule staying GET-scoped means that write still needs its own
                        // deliberate rule, not an accidental free pass from this one.
                        .requestMatchers(HttpMethod.GET, "/actuator/**").permitAll()
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        // This is the real default posture, not the GET-scoped rule above: every
                        // request this filter chain hasn't already matched a more specific rule for
                        // (including any non-GET /actuator/** or /ingress/v1/** path) is public —
                        // "public unless specifically gated", not "gated unless specifically
                        // public". A sibling service that needs the opposite default should replace
                        // this with .anyRequest().authenticated() (or .denyAll()), not just copy it.
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
