package com.ledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Event Ledger API")
                        .version("1.0.0")
                        .description("""
                                Financial transaction event ledger that handles:
                                - **Idempotent ingestion** — duplicate eventIds are safely ignored
                                - **Out-of-order events** — events are always returned in business-time order
                                - **Net balance computation** — CREDIT minus DEBIT, always correct
                                """)
                        .contact(new Contact().name("Ledger Team")));
    }
}
