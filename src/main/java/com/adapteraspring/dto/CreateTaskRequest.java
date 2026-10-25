package com.adapteraspring.dto;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record CreateTaskRequest(
         @NotNull
        JsonNode payload,
        JsonNode metadata
) {
}
