package com.yhou.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;

@Configuration
public class AppConfig {

    /**
     * HTTP client for external API calls (payment processor, OpenAI)
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Patient Copay Payment API")
                        .version("1.0")
                        .description("Backend service for managing patient copay charges and payments with healthcare workflow integration")
                        .contact(new Contact()
                                .name("API Support")
                                .email("support@yhou.demo.com")));
    }
}
