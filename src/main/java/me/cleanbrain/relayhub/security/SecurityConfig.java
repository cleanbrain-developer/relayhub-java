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
                        // Unlike every other public GET in this app (metadata: names, statuses,
                        // counts), an Attempt carries the actual request/response bodies exchanged
                        // with a Target — real payload content, not just outcome. That's sensitive
                        // enough to gate behind admin auth even though it's read-only (self-review
                        // finding, 2026-09-17). Confirmed safe: developer.cleanbrain.me's ported
                        // Live view (see the CORS bean above) reads /api/deliveries/summary and
                        // /api/live/stream's attemptId field but never actually calls either of
                        // these to resolve it, so this doesn't break that integration.
                        .requestMatchers(HttpMethod.GET, "/api/deliveries/*/attempts").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/delivery-attempts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .requestMatchers("/ingress/v1/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
