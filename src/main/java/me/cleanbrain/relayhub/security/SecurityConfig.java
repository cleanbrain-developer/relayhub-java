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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
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
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .requestMatchers("/ingress/v1/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
