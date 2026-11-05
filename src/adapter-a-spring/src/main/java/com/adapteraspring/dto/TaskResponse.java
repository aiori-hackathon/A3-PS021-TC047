package com.adapteraspring.dto;

import com.adapteraspring.entity.Tasks;
import com.adapteraspring.enumerates.StateEnum;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
        @JsonProperty("task_id") UUID taskId,
        @JsonProperty("state") StateEnum state,
        @JsonProperty("dialog_id") UUID dialogId,
        @JsonProperty("request_id") UUID requestId,
        @JsonProperty("attempt_count") Integer attemptCount,
        @JsonProperty("result") JsonNode result,
        @JsonProperty("last_error") String lastError,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
) {
    public static TaskResponse from(Tasks task) {
        return new TaskResponse(task.getTaskId(), task.getState(), task.getDialogId(),
                task.getRequestId(), task.getAttemptCount(), task.getResult(),
                task.getLastError(), task.getCreatedAt(), task.getUpdatedAt());
    }
}
