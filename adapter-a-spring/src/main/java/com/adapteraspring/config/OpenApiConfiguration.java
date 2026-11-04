package com.adapteraspring.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    public OpenAPI adapterAOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Adapter A API")
                        .description("Task submission and status API for Adapter A.")
                        .version("v1"));
    }
}
