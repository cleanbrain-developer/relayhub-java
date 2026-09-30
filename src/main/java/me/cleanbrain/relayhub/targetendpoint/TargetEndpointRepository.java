package me.cleanbrain.relayhub.targetendpoint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TargetEndpointRepository extends JpaRepository<TargetEndpoint, UUID> {

    /** Eagerly loads {@code target} — {@code open-in-view: false} means a plain derived query
     *  would leave it as an uninitialized lazy proxy once TargetEndpointResponse.from() (called
     *  from the controller, after this repository call's own transaction has closed) reads
     *  {@code target.getKey()}. Same reasoning as SourceEventRepository#findBySourceKeyAndKey. */
    @Query("select te from TargetEndpoint te join fetch te.target where te.target.key = :targetKey and te.key = :key")
    Optional<TargetEndpoint> findByTargetKeyAndKey(@Param("targetKey") String targetKey, @Param("key") String key);

    @Query("select te from TargetEndpoint te join fetch te.target where te.target.key = :targetKey")
    List<TargetEndpoint> findByTargetKey(@Param("targetKey") String targetKey);

    /** Hard-delete guard for Target — any status still holds a real FK to it. */
    long countByTarget_Id(UUID targetId);
}
