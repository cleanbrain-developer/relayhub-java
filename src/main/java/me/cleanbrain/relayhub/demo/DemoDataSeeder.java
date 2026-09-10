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
 * Idempotently registers a fixed set of demo Sources/Targets/Subscriptions on startup, only when
 * the "demo" Spring profile is active. Exists so a developer-facing observability dashboard has
 * something to show without requiring manual registration first — see
 * https://github.com/cleanbrain-developer/relayhub-demo-systems, the separate simulator service
 * that (a) calls these Ingress URLs on a timer to generate continuous traffic and (b) implements
 * demo-billing's random-failure behavior. RelayHub itself has no traffic-generation or
 * random-failure logic — that belongs to the external systems being simulated, not to RelayHub.
 *
 * <p>Registration reuses the same REST-facing services (and their validation/idempotency rules)
 * that the HTTP API uses — this is not a separate direct-repository shortcut.
 */
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final SourceService sourceService;
    private final SourceEventService sourceEventService;
    private final TargetService targetService;
    private final SubscriptionService subscriptionService;

    @Value("${relayhub.demo.simulator-base-url:http://localhost:9500}")
    private String simulatorBaseUrl;

    private static final String CUSTOMER_CREATED_TEMPLATE =
            "{\"dealerId\":\"${$.customerNo}\",\"dealerName\":\"${$.name}\"}";

    @Override
    public void run(String... args) {
        log.info("Seeding demo Sources/Targets/Subscriptions (simulator base URL: {})", simulatorBaseUrl);

        seedSource("demo-erp", "Demo ERP", "Simulated ERP system emitting customer changes");
        seedSource("demo-crm", "Demo CRM", "Simulated CRM system emitting customer changes");
        seedCustomerCreatedEvent("demo-erp");
        seedCustomerCreatedEvent("demo-crm");

        seedTarget("demo-warehouse", "Demo Warehouse", "Always succeeds — see relayhub-demo-systems");
        seedTarget("demo-billing", "Demo Billing", "Randomly fails — see relayhub-demo-systems");

        seedSubscription("demo-erp", "demo-warehouse", "/targets/warehouse");
        seedSubscription("demo-erp", "demo-billing", "/targets/billing");
        seedSubscription("demo-crm", "demo-warehouse", "/targets/warehouse");
        seedSubscription("demo-crm", "demo-billing", "/targets/billing");

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

    private void seedCustomerCreatedEvent(String sourceKey) {
        try {
            sourceEventService.getBySourceKeyAndKey(sourceKey, "customer-created");
        } catch (NotFoundException e) {
            sourceEventService.create(sourceKey, new SourceEventCreateRequest(
                    "customer-created", "Customer Created", "Customer created event",
                    "customer", Operation.CREATED, "$.customerNo", null, "$.customerNo", null, null));
            log.info("Seeded Source Event '{}/customer-created'", sourceKey);
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

    private void seedSubscription(String sourceKey, String targetKey, String targetPath) {
        String name = "%s -> %s".formatted(sourceKey, targetKey);
        // No direct "exists" lookup for Subscriptions (unlike Source/Target/SourceEvent, they have
        // no unique business key) — create() itself isn't guarded against duplicates on repeated
        // runs, so this checks for an existing active Subscription with the same descriptive name
        // instead, which is unique enough for this fixed demo dataset. seedCustomerCreatedEvent
        // already ran for both demo Sources by the time this is called, so the lookup is safe.
        var sourceEventId = sourceEventService.getBySourceKeyAndKey(sourceKey, "customer-created").getId();
        boolean alreadyExists = subscriptionService.findActiveForSourceEvent(sourceEventId).stream()
                .anyMatch(s -> s.getName().equals(name));
        if (alreadyExists) {
            return;
        }
        subscriptionService.create(new SubscriptionCreateRequest(
                sourceKey, "customer-created", targetKey, name,
                "Demo subscription: " + name, HttpVerb.POST, targetPath, CUSTOMER_CREATED_TEMPLATE, null));
        log.info("Seeded Subscription '{}'", name);
    }
}
