package io.opencode4j.agent.runtime.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opencode4j.runtime.tool.Param;
import io.opencode4j.runtime.tool.ToolContext;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Generates JSON Schema definitions from Java methods, records, and types for MCP tools.
 */
public final class JsonSchemaGenerator {

    private final ObjectMapper mapper;

    public JsonSchemaGenerator(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * Generates an inputSchema object from a Java tool method's parameters.
     */
    public ObjectNode schemaForMethod(Method method) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");

        for (Parameter parameter : method.getParameters()) {
            if (ToolContext.class.isAssignableFrom(parameter.getType())) {
                continue; // context is injected by the runtime, not supplied by the model
            }
            Param param = parameter.getAnnotation(Param.class);
            String paramName = param != null && !param.name().isBlank() ? param.name() : parameter.getName();
            ObjectNode prop = properties.putObject(paramName);
            populateTypeSchema(prop, parameter.getType());

            if (param != null && !param.description().isBlank()) {
                prop.put("description", param.description());
            }
            if (param == null || param.required()) {
                required.add(paramName);
            }
        }

        return schema;
    }

    /**
     * Generates a schema for a structured result class (e.g. record or bean).
     */
    public ObjectNode schemaForType(Class<?> targetType) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");

        if (targetType.isRecord()) {
            for (RecordComponent comp : targetType.getRecordComponents()) {
                String name = comp.getName();
                ObjectNode prop = properties.putObject(name);
                populateTypeSchema(prop, comp.getType());
                required.add(name);
            }
        } else {
            for (java.lang.reflect.Field field : targetType.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                String name = field.getName();
                ObjectNode prop = properties.putObject(name);
                populateTypeSchema(prop, field.getType());
                required.add(name);
            }
        }

        return schema;
    }

    private void populateTypeSchema(ObjectNode target, Class<?> type) {
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
            target.put("type", "integer");
        } else if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
            target.put("type", "number");
        } else if (type == boolean.class || type == Boolean.class) {
            target.put("type", "boolean");
        } else if (Collection.class.isAssignableFrom(type) || type.isArray()) {
            target.put("type", "array");
            Class<?> itemType = type.isArray() ? type.getComponentType() : String.class;
            ObjectNode items = target.putObject("items");
            populateTypeSchema(items, itemType);
        } else if (Map.class.isAssignableFrom(type)) {
            target.put("type", "object");
        } else if (type.isRecord()) {
            target.put("type", "object");
            ObjectNode subProps = target.putObject("properties");
            ArrayNode subReq = target.putArray("required");
            for (RecordComponent comp : type.getRecordComponents()) {
                ObjectNode subProp = subProps.putObject(comp.getName());
                populateTypeSchema(subProp, comp.getType());
                subReq.add(comp.getName());
            }
        } else {
            target.put("type", "string");
        }
    }
}
