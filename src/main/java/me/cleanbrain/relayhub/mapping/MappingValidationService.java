package me.cleanbrain.relayhub.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.sourcefield.SourceFieldRepository;
import me.cleanbrain.relayhub.sourceevent.SourceEvent;
import me.cleanbrain.relayhub.targetendpoint.TargetEndpoint;
import me.cleanbrain.relayhub.targetfield.TargetFieldRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Warns (never blocks) when a Subscription's {@code targetPayloadTemplate} references a target
 * field or source JSONPath that isn't registered in the Spec 006 field registry — "field registry
 * validation, warning only" (maintainer request 2026-09-30, follow-up to the integration-platform
 * overhaul). Spec 006 deliberately left this unvalidated ("the registry stays a documentation/
 * autocomplete aid, not a hard constraint") and this deliberately keeps that: nothing here rejects
 * a create/update, it only surfaces warnings on the response for the console to display.
 *
 * <p>Walks the template's full JSON tree, not just its top level, so a nested/array-shaped template
 * (which {@code MappingBuilder}'s row editor can't represent and falls back to raw-JSON for) still
 * gets checked. An object key anywhere in the tree is treated as a referenced target field; a
 * string value matching the {@code ${$...}} placeholder pattern (same regex {@link MappingService}
 * itself resolves against) is treated as a referenced source JSONPath.
 */
@Service
@RequiredArgsConstructor
public class MappingValidationService {

    private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{(\\$.*)}$");

    private final ObjectMapper objectMapper;
    private final SourceFieldRepository sourceFieldRepository;
    private final TargetFieldRepository targetFieldRepository;

    /**
     * Not valid JSON is not this method's concern (nothing here validates template syntax — that
     * surfaces at actual delivery time via {@link MappingService}) — returns no warnings rather
     * than failing, since an in-progress edit is routinely invalid JSON for a moment.
     */
    public List<String> validate(SourceEvent sourceEvent, TargetEndpoint targetEndpoint, String targetPayloadTemplate) {
        JsonNode templateNode;
        try {
            templateNode = objectMapper.readTree(targetPayloadTemplate);
        } catch (Exception e) {
            return List.of();
        }
        if (templateNode == null || !templateNode.isObject()) {
            return List.of();
        }

        Set<String> referencedTargetFields = new LinkedHashSet<>();
        Set<String> referencedJsonPaths = new LinkedHashSet<>();
        collectReferences(templateNode, referencedTargetFields, referencedJsonPaths);

        Set<String> registeredTargetFields = new TreeSet<>();
        targetFieldRepository.findByTargetKeyAndEndpointKey(targetEndpoint.getTarget().getKey(), targetEndpoint.getKey())
                .stream().filter(f -> f.getStatus() == Status.ACTIVE).forEach(f -> registeredTargetFields.add(f.getKey()));

        Set<String> registeredJsonPaths = new TreeSet<>();
        sourceFieldRepository.findBySourceKeyAndEventKey(sourceEvent.getSource().getKey(), sourceEvent.getKey())
                .stream().filter(f -> f.getStatus() == Status.ACTIVE).forEach(f -> registeredJsonPaths.add(f.getJsonPath()));

        List<String> warnings = new ArrayList<>();
        for (String field : referencedTargetFields) {
            if (!registeredTargetFields.contains(field)) {
                warnings.add("Target field \"%s\" is not registered for endpoint \"%s\" — mapping will still work, but it won't appear as a dropdown choice."
                        .formatted(field, targetEndpoint.getKey()));
            }
        }
        for (String jsonPath : referencedJsonPaths) {
            if (!registeredJsonPaths.contains(jsonPath)) {
                warnings.add("Source JSONPath \"%s\" is not registered for event \"%s\" — mapping will still work, but it won't appear as a dropdown choice."
                        .formatted(jsonPath, sourceEvent.getKey()));
            }
        }
        return warnings;
    }

    private void collectReferences(JsonNode node, Set<String> targetFields, Set<String> jsonPaths) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                targetFields.add(entry.getKey());
                collectReferences(entry.getValue(), targetFields, jsonPaths);
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectReferences(child, targetFields, jsonPaths));
        } else if (node.isTextual()) {
            Matcher matcher = PLACEHOLDER.matcher(node.textValue());
            if (matcher.matches()) {
                jsonPaths.add(matcher.group(1));
            }
        }
    }
}
