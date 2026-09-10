package me.cleanbrain.relayhub.demo;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.HttpVerb;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.source.SourceService;
import me.cleanbrain.relayhub.source.dto.SourceCreateRequest;
import me.cleanbrain.relayhub.sourceevent.Operation;
import me.cleanbrain.relayhub.sourceevent.SourceEventService;
import me.cleanbrain.relayhub.sourceevent.dto.SourceEventCreateRequest;
import me.cleanbrain.relayhub.subscription.SubscriptionService;
import me.cleanbrain.relayhub.subscription.dto.SubscriptionCreateRequest;
import me.cleanbrain.relayhub.target.TargetService;
import me.cleanbrain.relayhub.target.dto.TargetCreateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Idempotently registers a fixed demo scenario (Source/SourceEvents/Targets/Subscriptions) on
 * startup, only when the "demo" Spring profile is active. Exists so a developer-facing
 * observability dashboard has something to show without requiring manual registration first —
 * see https://github.com/cleanbrain-developer/relayhub-demo-systems, the separate simulator
 * service that (a) continuously generates flight-status events and posts them to RelayHub's
 * Ingress URLs and (b) implements demo-travelapp-vendor's random-failure behavior. RelayHub
 * itself has no traffic-generation or random-failure logic — that belongs to the external
 * systems being simulated, not to RelayHub.
 *
 * <p>Scenario: a simulated airline flight-status system (demo-flightstatus) emits a flight's
 * initial status (flight-created) and later status changes (flight-status-updated, e.g. gate
 * change, delay, boarding, departure). Two external systems subscribe: demo-airport-display
 * (an internal-feeling always-succeeding target that needs every field) and
 * demo-travelapp-vendor (a third-party travel app that only needs flight number + status, and
 * whose flaky API is the source of DLQ activity for the observability demo).
 *
 * <p>Registration reuses the same REST-facing services (and their validation/idempotency rules)
 * that the HTTP API uses — this is not a separate direct-repository shortcut.
 */
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String SOURCE_KEY = "demo-flightstatus";
    private static final String FLIGHT_CREATED = "flight-created";
    private static final String FLIGHT_STATUS_UPDATED = "flight-status-updated";

    private final SourceService sourceService;
    private final SourceEventService sourceEventService;
    private final TargetService targetService;
    private final SubscriptionService subscriptionService;

    @Value("${relayhub.demo.simulator-base-url:http://localhost:9500}")
    private String simulatorBaseUrl;

    private static final String AIRPORT_DISPLAY_TEMPLATE =
            "{\"flightNo\":\"${$.flightNo}\",\"status\":\"${$.status}\",\"gate\":\"${$.gate}\","
                    + "\"delayMinutes\":\"${$.delayMinutes}\"}";
    private static final String TRAVELAPP_VENDOR_TEMPLATE =
            "{\"flightNo\":\"${$.flightNo}\",\"status\":\"${$.status}\"}";

    @Override
    public void run(String... args) {
        log.info("Seeding demo Source/Targets/Subscriptions (simulator base URL: {})", simulatorBaseUrl);

        seedSource(SOURCE_KEY, "Demo Flight Status", "Simulated airline flight-status system");
        seedFlightEvent(FLIGHT_CREATED, "Flight Created", "Initial flight status published", Operation.CREATED);
        seedFlightEvent(FLIGHT_STATUS_UPDATED, "Flight Status Updated", "Flight status changed (gate/delay/boarding/departure)", Operation.PATCHED);

        seedTarget("demo-airport-display", "Demo Airport Display", "Always succeeds — see relayhub-demo-systems");
        seedTarget("demo-travelapp-vendor", "Demo Travel App Vendor", "Randomly fails/times out — see relayhub-demo-systems");

        seedSubscription(FLIGHT_CREATED, "demo-airport-display", "/targets/airport-display", AIRPORT_DISPLAY_TEMPLATE);
        seedSubscription(FLIGHT_CREATED, "demo-travelapp-vendor", "/targets/travelapp-vendor", TRAVELAPP_VENDOR_TEMPLATE);
        seedSubscription(FLIGHT_STATUS_UPDATED, "demo-airport-display", "/targets/airport-display", AIRPORT_DISPLAY_TEMPLATE);
        seedSubscription(FLIGHT_STATUS_UPDATED, "demo-travelapp-vendor", "/targets/travelapp-vendor", TRAVELAPP_VENDOR_TEMPLATE);

        log.info("Demo data seeding complete");
    }

    private void seedSource(String key, String name, String description) {
        try {
            sourceService.getByKey(key);
        } catch (NotFoundException e) {
            sourceService.create(new SourceCreateRequest(key, name, description, null));
            log.info("Seeded Source '{}'", key);
        }
    }

    private void seedFlightEvent(String eventKey, String name, String description, Operation operation) {
        try {
            sourceEventService.getBySourceKeyAndKey(SOURCE_KEY, eventKey);
        } catch (NotFoundException e) {
            sourceEventService.create(SOURCE_KEY, new SourceEventCreateRequest(
                    eventKey, name, description, "flight", operation,
                    "$.flightNo", null, "$.eventId", null, null));
            log.info("Seeded Source Event '{}/{}'", SOURCE_KEY, eventKey);
        }
    }

    private void seedTarget(String key, String name, String description) {
        try {
            targetService.getByKey(key);
        } catch (NotFoundException e) {
            targetService.create(new TargetCreateRequest(key, name, description, simulatorBaseUrl, null));
            log.info("Seeded Target '{}'", key);
        }
    }

    private void seedSubscription(String eventKey, String targetKey, String targetPath, String template) {
        String name = "%s -> %s".formatted(eventKey, targetKey);
        // No direct "exists" lookup for Subscriptions (unlike Source/Target/SourceEvent, they have
        // no unique business key) — create() itself isn't guarded against duplicates on repeated
        // runs, so this checks for an existing active Subscription with the same descriptive name
        // instead, which is unique enough for this fixed demo dataset. seedFlightEvent already ran
        // for both event keys by the time this is called, so the lookup is safe.
        var sourceEventId = sourceEventService.getBySourceKeyAndKey(SOURCE_KEY, eventKey).getId();
        boolean alreadyExists = subscriptionService.findActiveForSourceEvent(sourceEventId).stream()
                .anyMatch(s -> s.getName().equals(name));
        if (alreadyExists) {
            return;
        }
        subscriptionService.create(new SubscriptionCreateRequest(
                SOURCE_KEY, eventKey, targetKey, name,
                "Demo subscription: " + name, HttpVerb.POST, targetPath, template, null));
        log.info("Seeded Subscription '{}'", name);
    }
}
