package com.adapteraspring.b;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

public record BRequest(
        @JsonProperty("dialog_id") String dialogId,
        @JsonProperty("task_id") String taskId,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("payload") JsonNode payload
) {
    public static BRequest of(UUID dialogId, UUID taskId, UUID requestId, JsonNode payload) {
        return new BRequest(dialogId.toString(), taskId.toString(), requestId.toString(), payload);
    }
}
