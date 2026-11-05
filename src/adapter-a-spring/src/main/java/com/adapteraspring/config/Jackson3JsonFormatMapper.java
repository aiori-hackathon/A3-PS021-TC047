package com.adapteraspring.config;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Lets Hibernate read and write JSON columns using Jackson 3 JsonNode. */
public class Jackson3JsonFormatMapper implements FormatMapper {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T fromString(CharSequence source, JavaType<T> javaType, WrapperOptions options) {
        requireJsonNode(javaType);
        return (T) mapper.readTree(source.toString());
    }

    @Override
    public <T> String toString(T value, JavaType<T> javaType, WrapperOptions options) {
        requireJsonNode(javaType);
        return mapper.writeValueAsString(value);
    }

    private static void requireJsonNode(JavaType<?> javaType) {
        if (!JsonNode.class.isAssignableFrom(javaType.getJavaTypeClass())) {
            throw new UnsupportedOperationException(
                    "Only JsonNode JSON columns are supported, got " + javaType.getJavaTypeClass());
        }
    }
}