package com.adapteraspring.validation;

import com.adapteraspring.exception.InvalidPayloadException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Map;

@Component
public class PayloadValidator {
    public void validate(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new InvalidPayloadException("payload must be an object");
        }
        requireKeys(payload, "action", "parameters");
        JsonNode action = payload.get("action");
        if (action == null || !action.isTextual() || !"record_order".equals(action.textValue())) {
            throw new InvalidPayloadException("action must be record_order");
        }

        JsonNode parameters = payload.get("parameters");
        if (parameters == null || !parameters.isObject()) {
            throw new InvalidPayloadException("parameters must be an object");
        }
        requireKeys(parameters, "item", "quantity");

        JsonNode item = parameters.get("item");
        if (item == null || !item.isTextual() || item.textValue().length() < 1
                || item.textValue().length() > 64) {
            throw new InvalidPayloadException("parameters.item must be 1-64 characters");
        }

        JsonNode quantity = parameters.get("quantity");
        if (quantity == null || !quantity.isIntegralNumber()
                || quantity.intValue() < 1 || quantity.intValue() > 1000) {
            throw new InvalidPayloadException("parameters.quantity must be an integer from 1 to 1000");
        }
    }

    private void requireKeys(JsonNode node, String... expected) {
        if (node.size() != expected.length) {
            throw new InvalidPayloadException("payload contains unexpected keys");
        }
        for (String key : expected) {
            if (!node.has(key)) {
                throw new InvalidPayloadException("payload is missing " + key);
            }
        }
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            String actual = field.getKey();
            boolean known = false;
            for (String key : expected) {
                if (key.equals(actual)) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                throw new InvalidPayloadException("payload contains unexpected key: " + actual);
            }
        }
    }
}
