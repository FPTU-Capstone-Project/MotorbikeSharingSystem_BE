package com.mssus.app.appconfig.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${app.version}")
    private String appVersion;

    @Bean
    public OpenAPI customOpenAPI() {
        // Production server
        Server productionServer = new Server();
        productionServer.setUrl("http://13.215.143.121");
        productionServer.setDescription("Production Server (AWS EC2)");
        
        // Local development server
        Server localServer = new Server();
        localServer.setUrl("http://localhost:8080");
        localServer.setDescription("Local Development Server");
        
        return new OpenAPI()
                .servers(List.of(productionServer, localServer))
                .info(new Info()
                        .title("MSSUS Account Service API")
                        .version(appVersion)
                        .description("Account module API for Motorbike Sharing System for University Students")
                        .contact(new Contact()
                                .name("MSSUS Development Team")
                                .email("support@mssus.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("http://www.apache.org/licenses/LICENSE-2.0.html")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .name("bearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT authentication token")));
    }
}
