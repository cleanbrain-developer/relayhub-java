package me.cleanbrain.relayhub.sourceevent;

import me.cleanbrain.relayhub.common.HttpVerb;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceEventRepository extends JpaRepository<SourceEvent, UUID> {

    /**
     * Eagerly loads {@code source} — {@code open-in-view: false} (see application.yml) means a
     * plain derived query would leave it as an uninitialized lazy proxy, and
     * SourceEventResponse.from() (called from the controller, after this repository call's own
     * transaction has already closed) reads {@code source.getKey()}. Without this, GET
     * /api/sources/{sourceKey}/events/{key} throws LazyInitializationException — a real, latent
     * bug from Spec 001 that Spec 005's admin console surfaced (nothing had called this endpoint
     * before, including tests).
     */
    @Query("select se from SourceEvent se join fetch se.source where se.source.key = :sourceKey and se.key = :key")
    Optional<SourceEvent> findBySourceKeyAndKey(@Param("sourceKey") String sourceKey, @Param("key") String key);

    Optional<SourceEvent> findByIngressPathAndIngressMethod(String ingressPath, HttpVerb ingressMethod);

    /** Same eager-load reasoning as {@link #findBySourceKeyAndKey} — used by the Spec 005 list endpoint. */
    @Query("select se from SourceEvent se join fetch se.source where se.source.key = :sourceKey")
    List<SourceEvent> findBySourceKey(@Param("sourceKey") String sourceKey);
}
