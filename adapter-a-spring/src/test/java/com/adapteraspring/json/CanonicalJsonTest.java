package com.adapteraspring.json;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanonicalJsonTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void sortsKeysAndRemovesWhitespace() {
        String canonical = CanonicalJson.canonicalize(mapper.readTree(
                "{ \"parameters\": { \"quantity\": 3, \"item\": \"widget\" }, \"action\": \"record_order\" }"));
        assertEquals("{\"action\":\"record_order\",\"parameters\":{\"item\":\"widget\",\"quantity\":3}}",
                canonical);
    }

    @Test
    void matchesRecordOrderHash() {
        String canonical = CanonicalJson.canonicalize(mapper.readTree(
                "{\"action\":\"record_order\",\"parameters\":{\"item\":\"widget\",\"quantity\":3}}"));
        assertEquals("8a0b2b1f616efe8d5e14590a6c154f7dd7c9962fad8daf9ae80bb45dbb9c055e",
                CanonicalJson.sha256Hex(canonical));
    }

    @Test
    void jsonRoundTripPreservesCanonicalInteger() {
        var original = mapper.readTree(
                "{\"action\":\"record_order\",\"parameters\":{\"item\":\"widget\",\"quantity\":3}}");
        var roundTrip = mapper.readTree(original.toString());
        assertEquals(CanonicalJson.canonicalize(original), CanonicalJson.canonicalize(roundTrip));
    }
}
