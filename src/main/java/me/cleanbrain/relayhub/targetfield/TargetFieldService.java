package me.cleanbrain.relayhub.targetfield;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.target.Target;
import me.cleanbrain.relayhub.target.TargetService;
import me.cleanbrain.relayhub.targetfield.dto.TargetFieldCreateRequest;
import me.cleanbrain.relayhub.targetfield.dto.TargetFieldUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TargetFieldService {

    private final TargetFieldRepository targetFieldRepository;
    private final TargetService targetService;

    @Transactional
    public TargetField create(String targetKey, TargetFieldCreateRequest request) {
        Target target = targetService.getByKey(targetKey);

        targetFieldRepository.findByTargetKeyAndFieldKey(targetKey, request.key()).ifPresent(existing -> {
            throw new IllegalArgumentException("Target Field already registered: %s/%s".formatted(targetKey, request.key()));
        });

        TargetField field = TargetField.builder()
                .target(target)
                .key(request.key())
                .dataType(request.dataType())
                .description(request.description())
                .exampleValue(request.exampleValue())
                .required(request.required())
                .sensitive(request.sensitive())
                .status(Status.ACTIVE)
                .build();

        return targetFieldRepository.save(field);
    }

    public List<TargetField> findByTargetKey(String targetKey) {
        return targetFieldRepository.findByTargetKey(targetKey);
    }

    public TargetField getByTargetKeyAndFieldKey(String targetKey, String fieldKey) {
        return targetFieldRepository.findByTargetKeyAndFieldKey(targetKey, fieldKey)
                .orElseThrow(() -> new NotFoundException("Target Field not found: %s/%s".formatted(targetKey, fieldKey)));
    }

    @Transactional
    public TargetField update(String targetKey, String fieldKey, TargetFieldUpdateRequest request) {
        TargetField field = getByTargetKeyAndFieldKey(targetKey, fieldKey);
        field.setDataType(request.dataType());
        field.setDescription(request.description());
        field.setExampleValue(request.exampleValue());
        field.setRequired(request.required());
        field.setSensitive(request.sensitive());
        return field;
    }

    /** Soft delete: flips status to INACTIVE. Row stays — same reasoning as SourceFieldService. */
    @Transactional
    public void deactivate(String targetKey, String fieldKey) {
        TargetField field = getByTargetKeyAndFieldKey(targetKey, fieldKey);
        field.setStatus(Status.INACTIVE);
    }

    /** Permanently removes the row — admin-only. No dependents reference a TargetField's id. */
    @Transactional
    public void hardDelete(String targetKey, String fieldKey) {
        TargetField field = getByTargetKeyAndFieldKey(targetKey, fieldKey);
        targetFieldRepository.delete(field);
    }
}
