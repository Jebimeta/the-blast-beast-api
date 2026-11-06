package com.theblastbeast.config;

import com.theblastbeast.properties.SiteProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class OpenApiConfig {

    private final SiteProperties siteProperties;

    @Bean
    public OpenAPI blastBeastOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("The Blast Beast API")
                        .version("0.1.0")
                        .description("API del portal de noticias de heavy metal The Blast Beast")
                        .contact(new Contact().name("The Blast Beast"))
                        .license(new License().name("MIT")))
                .servers(List.of(new Server().url(siteProperties.baseUrl()).description("Cliente")));
    }
}
