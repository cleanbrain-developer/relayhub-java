package me.cleanbrain.relayhub.targetfield;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TargetFieldRepository extends JpaRepository<TargetField, UUID> {

    /** Eagerly loads target — same open-in-view:false reasoning as SourceFieldRepository. */
    @Query("select f from TargetField f join fetch f.target t where t.key = :targetKey order by f.key")
    List<TargetField> findByTargetKey(@Param("targetKey") String targetKey);

    @Query("select f from TargetField f join fetch f.target t where t.key = :targetKey and f.key = :fieldKey")
    Optional<TargetField> findByTargetKeyAndFieldKey(@Param("targetKey") String targetKey, @Param("fieldKey") String fieldKey);

    long countByTarget_Id(UUID targetId);
}
