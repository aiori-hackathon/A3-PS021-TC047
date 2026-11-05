package com.adapteraspring.validation;

import com.adapteraspring.exception.InvalidPayloadException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayloadValidatorTest {
    private final PayloadValidator validator = new PayloadValidator();
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void acceptsValidPayload() {
        assertDoesNotThrow(() -> validator.validate(mapper.readTree(
                "{\"action\":\"record_order\",\"parameters\":{\"item\":\"widget\",\"quantity\":3}}")));
    }

    @Test
    void rejectsExtraKey() {
        assertThrows(InvalidPayloadException.class, () -> validator.validate(mapper.readTree(
                "{\"action\":\"record_order\",\"parameters\":{\"item\":\"widget\",\"quantity\":3,\"extra\":true}}")));
    }

    @Test
    void rejectsZeroQuantity() {
        assertThrows(InvalidPayloadException.class, () -> validator.validate(mapper.readTree(
                "{\"action\":\"record_order\",\"parameters\":{\"item\":\"widget\",\"quantity\":0}}")));
    }

    @Test
    void rejectsDecimalQuantity() {
        assertThrows(InvalidPayloadException.class, () -> validator.validate(mapper.readTree(
                "{\"action\":\"record_order\",\"parameters\":{\"item\":\"widget\",\"quantity\":3.0}}")));
    }

    @Test
    void rejectsMissingRequiredKey() {
        assertThrows(InvalidPayloadException.class, () -> validator.validate(mapper.readTree(
                "{\"action\":\"record_order\"}")));
    }
}
