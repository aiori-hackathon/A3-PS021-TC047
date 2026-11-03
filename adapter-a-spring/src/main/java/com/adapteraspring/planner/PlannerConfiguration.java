package com.adapteraspring.planner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlannerConfiguration {
    @Bean
    Planner planner(@Value("${planner.mode:passthrough}") String mode) {
        if (!"passthrough".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("Unsupported planner.mode: " + mode
                    + ". Only passthrough is supported.");
        }
        return new PassthroughPlanner();
    }
}
