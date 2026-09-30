package me.cleanbrain.relayhub.targetfield;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.targetendpoint.TargetEndpoint;
import me.cleanbrain.relayhub.targetendpoint.TargetEndpointService;
import me.cleanbrain.relayhub.targetfield.dto.TargetFieldCreateRequest;
import me.cleanbrain.relayhub.targetfield.dto.TargetFieldUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TargetFieldService {

    private final TargetFieldRepository targetFieldRepository;
    private final TargetEndpointService targetEndpointService;

    @Transactional
    public TargetField create(String targetKey, String endpointKey, TargetFieldCreateRequest request) {
        TargetEndpoint endpoint = targetEndpointService.getByTargetKeyAndKey(targetKey, endpointKey);

        targetFieldRepository.findByTargetKeyAndEndpointKeyAndFieldKey(targetKey, endpointKey, request.key()).ifPresent(existing -> {
            throw new IllegalArgumentException(
                    "Target Field already registered: %s/%s/%s".formatted(targetKey, endpointKey, request.key()));
        });

        TargetField field = TargetField.builder()
                .targetEndpoint(endpoint)
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

    public List<TargetField> findByTargetKeyAndEndpointKey(String targetKey, String endpointKey) {
        return targetFieldRepository.findByTargetKeyAndEndpointKey(targetKey, endpointKey);
    }

    public TargetField getByTargetKeyAndEndpointKeyAndFieldKey(String targetKey, String endpointKey, String fieldKey) {
        return targetFieldRepository.findByTargetKeyAndEndpointKeyAndFieldKey(targetKey, endpointKey, fieldKey)
                .orElseThrow(() -> new NotFoundException(
                        "Target Field not found: %s/%s/%s".formatted(targetKey, endpointKey, fieldKey)));
    }

    @Transactional
    public TargetField update(String targetKey, String endpointKey, String fieldKey, TargetFieldUpdateRequest request) {
        TargetField field = getByTargetKeyAndEndpointKeyAndFieldKey(targetKey, endpointKey, fieldKey);
        field.setDataType(request.dataType());
        field.setDescription(request.description());
        field.setExampleValue(request.exampleValue());
        field.setRequired(request.required());
        field.setSensitive(request.sensitive());
        return field;
    }

    /** Soft delete: flips status to INACTIVE. Row stays — same reasoning as SourceFieldService. */
    @Transactional
    public void deactivate(String targetKey, String endpointKey, String fieldKey) {
        TargetField field = getByTargetKeyAndEndpointKeyAndFieldKey(targetKey, endpointKey, fieldKey);
        field.setStatus(Status.INACTIVE);
    }

    /** Permanently removes the row — admin-only. No dependents reference a TargetField's id. */
    @Transactional
    public void hardDelete(String targetKey, String endpointKey, String fieldKey) {
        TargetField field = getByTargetKeyAndEndpointKeyAndFieldKey(targetKey, endpointKey, fieldKey);
        targetFieldRepository.delete(field);
    }
}
