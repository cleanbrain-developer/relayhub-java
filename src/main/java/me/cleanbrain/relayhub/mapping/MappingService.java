package me.cleanbrain.relayhub.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps a Canonical Event's Source payload to a Target payload using a JSON template whose
 * string values may be JSONPath placeholders ({@code "${$.customerNo}"}). Implemented via
 * Jackson JsonNode tree manipulation — never naive string replace() — so number/boolean/
 * object/array types from the Source payload are preserved. See
 * docs/architecture/system-design.md ("Mapping strategy").
 */
@Service
@RequiredArgsConstructor
public class MappingService {

    private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{(\\$.*)}$");

    private static final Configuration JSONPATH_CONFIGURATION = Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .build();

    private final ObjectMapper objectMapper;

    public JsonNode map(JsonNode sourcePayload, String targetPayloadTemplate) {
        JsonNode templateNode;
        try {
            templateNode = objectMapper.readTree(targetPayloadTemplate);
        } catch (JsonProcessingException e) {
            throw new MappingException("Target payload template is not valid JSON", e);
        }
        DocumentContext context = JsonPath.using(JSONPATH_CONFIGURATION).parse(sourcePayload);
        return resolve(templateNode, context);
    }

    private JsonNode resolve(JsonNode node, DocumentContext context) {
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> result.set(entry.getKey(), resolve(entry.getValue(), context)));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            node.forEach(child -> result.add(resolve(child, context)));
            return result;
        }
        if (node.isTextual()) {
            Matcher matcher = PLACEHOLDER.matcher(node.textValue());
            if (matcher.matches()) {
                String jsonPath = matcher.group(1);
                try {
                    JsonNode value = context.read(jsonPath, JsonNode.class);
                    return value != null ? value : NullNode.getInstance();
                } catch (PathNotFoundException e) {
                    throw new MappingException("JSONPath not found in source payload: " + jsonPath, e);
                }
            }
        }
        // Literal value (string, number, boolean, null) — preserved as-is.
        return node;
    }
}
