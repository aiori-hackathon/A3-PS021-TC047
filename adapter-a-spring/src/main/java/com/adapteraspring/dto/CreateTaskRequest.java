package com.adapteraspring.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record CreateTaskRequest(
        @JsonProperty("payload") @NotNull JsonNode payload
) {
}
