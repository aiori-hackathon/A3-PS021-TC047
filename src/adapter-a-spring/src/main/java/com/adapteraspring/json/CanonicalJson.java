package com.adapteraspring.json;

import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;

public final class CanonicalJson {
    private CanonicalJson() {
    }

    public static String canonicalize(JsonNode node) {
        StringBuilder output = new StringBuilder();
        append(node, output);
        return output.toString();
    }

    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void append(JsonNode node, StringBuilder output) {
        if (node == null || node.isNull()) {
            output.append("null");
        } else if (node.isObject()) {
            output.append('{');
            var entries = node.properties().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                    .toList();
            for (int index = 0; index < entries.size(); index++) {
                if (index > 0) {
                    output.append(',');
                }
                Map.Entry<String, JsonNode> entry = entries.get(index);
                        appendString(entry.getKey(), output);
                        output.append(':');
                        append(entry.getValue(), output);
            }
            output.append('}');
        } else if (node.isArray()) {
            output.append('[');
            for (int index = 0; index < node.size(); index++) {
                if (index > 0) {
                    output.append(',');
                }
                append(node.get(index), output);
            }
            output.append(']');
        } else if (node.isTextual()) {
            appendString(node.textValue(), output);
        } else if (node.isIntegralNumber()) {
            output.append(node.longValue());
        } else {
            output.append(node.toString());
        }
    }

    private static void appendString(String value, StringBuilder output) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) {
                        output.append(String.format("\\u%04x", (int) character));
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        output.append('"');
    }
}
