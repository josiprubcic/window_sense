package com.windowsense.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI windowSenseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("WindowSense API")
                        .version("0.1.0")
                        .description("REST API za WindowSense telemetriju, komande, automatizaciju i ThingsBoard status."));
    }
}
