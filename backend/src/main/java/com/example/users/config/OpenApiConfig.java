package com.example.users.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI usersOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Users API")
                .description("Paginated, sortable user listing with order counts and salaries.")
                .version("v1"));
    }
}
