package me.cleanbrain.relayhub.targetfield;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TargetFieldRepository extends JpaRepository<TargetField, UUID> {

    /** Eagerly loads targetEndpoint (+ its target) — same open-in-view:false reasoning as
     *  SourceFieldRepository. */
    @Query("select f from TargetField f join fetch f.targetEndpoint te join fetch te.target t " +
            "where t.key = :targetKey and te.key = :endpointKey order by f.key")
    List<TargetField> findByTargetKeyAndEndpointKey(@Param("targetKey") String targetKey, @Param("endpointKey") String endpointKey);

    @Query("select f from TargetField f join fetch f.targetEndpoint te join fetch te.target t " +
            "where t.key = :targetKey and te.key = :endpointKey and f.key = :fieldKey")
    Optional<TargetField> findByTargetKeyAndEndpointKeyAndFieldKey(@Param("targetKey") String targetKey,
                                                                     @Param("endpointKey") String endpointKey,
                                                                     @Param("fieldKey") String fieldKey);

    long countByTargetEndpoint_Id(UUID targetEndpointId);
}
