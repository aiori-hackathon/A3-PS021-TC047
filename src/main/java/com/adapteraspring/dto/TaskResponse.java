package com.adapteraspring.dto;

import com.adapteraspring.enumerates.StateEnum;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record TaskResponse (
        UUID task_id,
        StateEnum state,
        UUID dialog_id,
        UUID request_id,
        Integer attempt_count,
        JsonNode result,
        String last_error,
        Instant created_at,
        Instant updated_at


) {
}
