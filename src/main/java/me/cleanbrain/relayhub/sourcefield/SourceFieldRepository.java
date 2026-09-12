package me.cleanbrain.relayhub.sourcefield;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceFieldRepository extends JpaRepository<SourceField, UUID> {

    /** Eagerly loads sourceEvent.source — same open-in-view:false reasoning as
     *  SourceEventRepository: SourceFieldResponse.from() reads sourceEvent.getSource().getKey()
     *  and sourceEvent.getKey() after this repository call's transaction has closed. */
    @Query("select f from SourceField f join fetch f.sourceEvent se join fetch se.source "
            + "where se.source.key = :sourceKey and se.key = :eventKey order by f.key")
    List<SourceField> findBySourceKeyAndEventKey(@Param("sourceKey") String sourceKey, @Param("eventKey") String eventKey);

    @Query("select f from SourceField f join fetch f.sourceEvent se join fetch se.source "
            + "where se.source.key = :sourceKey and se.key = :eventKey and f.key = :fieldKey")
    Optional<SourceField> findBySourceKeyAndEventKeyAndFieldKey(
            @Param("sourceKey") String sourceKey, @Param("eventKey") String eventKey, @Param("fieldKey") String fieldKey);

    long countBySourceEvent_Id(UUID sourceEventId);
}
