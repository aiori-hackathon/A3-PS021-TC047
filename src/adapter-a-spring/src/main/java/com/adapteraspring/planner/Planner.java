package com.adapteraspring.planner;

import tools.jackson.databind.JsonNode;

public interface Planner {
    JsonNode plan(JsonNode payload);
}
