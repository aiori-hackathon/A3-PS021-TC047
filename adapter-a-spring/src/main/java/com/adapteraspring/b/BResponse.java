package com.adapteraspring.b;

import tools.jackson.databind.JsonNode;

public record BResponse(String dialogId, String taskId, String requestId, String status,
                        JsonNode result, boolean duplicate, String message) {
}
