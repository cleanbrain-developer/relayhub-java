package me.cleanbrain.relayhub.sourcefield;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.sourceevent.SourceEvent;
import me.cleanbrain.relayhub.sourceevent.SourceEventService;
import me.cleanbrain.relayhub.sourcefield.dto.SourceFieldCreateRequest;
import me.cleanbrain.relayhub.sourcefield.dto.SourceFieldUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SourceFieldService {

    private final SourceFieldRepository sourceFieldRepository;
    private final SourceEventService sourceEventService;

    @Transactional
    public SourceField create(String sourceKey, String eventKey, SourceFieldCreateRequest request) {
        SourceEvent sourceEvent = sourceEventService.getBySourceKeyAndKey(sourceKey, eventKey);

        sourceFieldRepository.findBySourceKeyAndEventKeyAndFieldKey(sourceKey, eventKey, request.key()).ifPresent(existing -> {
            throw new IllegalArgumentException(
                    "Source Field already registered: %s/%s/%s".formatted(sourceKey, eventKey, request.key()));
        });

        SourceField field = SourceField.builder()
                .sourceEvent(sourceEvent)
                .key(request.key())
                .jsonPath(request.jsonPath())
                .dataType(request.dataType())
                .description(request.description())
                .exampleValue(request.exampleValue())
                .required(request.required())
                .sensitive(request.sensitive())
                .status(Status.ACTIVE)
                .build();

        return sourceFieldRepository.save(field);
    }

    public List<SourceField> findBySourceKeyAndEventKey(String sourceKey, String eventKey) {
        return sourceFieldRepository.findBySourceKeyAndEventKey(sourceKey, eventKey);
    }

    public SourceField getBySourceKeyAndEventKeyAndFieldKey(String sourceKey, String eventKey, String fieldKey) {
        return sourceFieldRepository.findBySourceKeyAndEventKeyAndFieldKey(sourceKey, eventKey, fieldKey)
                .orElseThrow(() -> new NotFoundException(
                        "Source Field not found: %s/%s/%s".formatted(sourceKey, eventKey, fieldKey)));
    }

    @Transactional
    public SourceField update(String sourceKey, String eventKey, String fieldKey, SourceFieldUpdateRequest request) {
        SourceField field = getBySourceKeyAndEventKeyAndFieldKey(sourceKey, eventKey, fieldKey);
        field.setJsonPath(request.jsonPath());
        field.setDataType(request.dataType());
        field.setDescription(request.description());
        field.setExampleValue(request.exampleValue());
        field.setRequired(request.required());
        field.setSensitive(request.sensitive());
        return field;
    }

    /** Soft delete: flips status to INACTIVE. Row stays — a Subscription mapping may still reference
     *  this field's key/jsonPath in its (free-text) targetPayloadTemplate; see spec.md's "Deliberately
     *  out of scope" — nothing validates a mapping against the registry, so nothing breaks either way. */
    @Transactional
    public void deactivate(String sourceKey, String eventKey, String fieldKey) {
        SourceField field = getBySourceKeyAndEventKeyAndFieldKey(sourceKey, eventKey, fieldKey);
        field.setStatus(Status.INACTIVE);
    }

    /** Permanently removes the row — admin-only. No dependents reference a SourceField's id (the
     *  registry is documentation/autocomplete, not a hard constraint — see spec.md), so no guard. */
    @Transactional
    public void hardDelete(String sourceKey, String eventKey, String fieldKey) {
        SourceField field = getBySourceKeyAndEventKeyAndFieldKey(sourceKey, eventKey, fieldKey);
        sourceFieldRepository.delete(field);
    }
}
