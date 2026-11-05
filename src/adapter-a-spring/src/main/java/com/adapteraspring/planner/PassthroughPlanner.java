package com.adapteraspring.planner;

import tools.jackson.databind.JsonNode;

public class PassthroughPlanner implements Planner {
    @Override
    public JsonNode plan(JsonNode payload) {
        return payload;
    }
}
