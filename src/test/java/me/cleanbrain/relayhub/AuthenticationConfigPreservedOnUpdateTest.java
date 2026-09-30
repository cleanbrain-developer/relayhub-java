package me.cleanbrain.relayhub;

import me.cleanbrain.relayhub.source.SourceRepository;
import me.cleanbrain.relayhub.source.SourceService;
import me.cleanbrain.relayhub.source.dto.SourceCreateRequest;
import me.cleanbrain.relayhub.source.dto.SourceUpdateRequest;
import me.cleanbrain.relayhub.common.AuthenticationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 3 of the integration-platform overhaul (maintainer request 2026-09-30) surfaced this while
 * building the admin console's Authentication tab: {@code SourceResponse}/{@code TargetResponse}
 * never echo {@code authenticationConfig} back (it's a secret), so the console's edit form can only
 * ever send a brand-new value or leave the field blank. Before this fix, {@code SourceService.update}
 * (and the identical {@code TargetService.update}) overwrote the stored secret with whatever came
 * in unconditionally — sending blank (the common case: editing just the name) silently wiped out a
 * working secret. Blank/null must leave the existing value untouched; only a real replacement value
 * should overwrite it. Verified directly against the repository, since the value is never readable
 * back through the API by design.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = "relayhub.delivery-tasks")
@ActiveProfiles("test")
class AuthenticationConfigPreservedOnUpdateTest {

    @Autowired
    SourceService sourceService;

    @Autowired
    SourceRepository sourceRepository;

    @Test
    @Transactional
    void blankAuthenticationConfigOnUpdateDoesNotClearAnExistingSecret() {
        sourceService.create(new SourceCreateRequest(
                "auth-preserve-source", "Auth Preserve Source", "x", AuthenticationType.API_KEY, "sk-original-secret"));

        // Editing just the name, leaving authenticationConfig blank -- must NOT clear the secret.
        sourceService.update("auth-preserve-source",
                new SourceUpdateRequest("Renamed", "still x", AuthenticationType.API_KEY, ""));
        assertThat(sourceRepository.findByKey("auth-preserve-source").orElseThrow().getAuthenticationConfig())
                .as("a blank authenticationConfig on update must leave the existing secret untouched")
                .isEqualTo("sk-original-secret");

        // The same with a null (not just blank) authenticationConfig.
        sourceService.update("auth-preserve-source",
                new SourceUpdateRequest("Renamed again", "still x", AuthenticationType.API_KEY, null));
        assertThat(sourceRepository.findByKey("auth-preserve-source").orElseThrow().getAuthenticationConfig())
                .isEqualTo("sk-original-secret");

        // A real replacement value still actually replaces it.
        sourceService.update("auth-preserve-source",
                new SourceUpdateRequest("Renamed again", "still x", AuthenticationType.API_KEY, "sk-new-secret"));
        assertThat(sourceRepository.findByKey("auth-preserve-source").orElseThrow().getAuthenticationConfig())
                .isEqualTo("sk-new-secret");
    }
}
