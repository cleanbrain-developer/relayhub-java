package me.cleanbrain.relayhub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

// A dedicated H2 database name: this test's Spring context differs from the other test classes'
// (no @EmbeddedKafka, default MOCK web environment), so it gets its own cached ApplicationContext.
// Without a unique DB name it would share the H2 instance named "relayhub" with those contexts —
// this context's create-drop teardown then drops tables out from under their still-running
// @Scheduled OutboxPublisher, breaking with "table not found" (observed when Spec 003 added it).
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:relayhub-contextload;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class RelayHubApplicationTests {

    @Test
    void contextLoads() {
    }
}
