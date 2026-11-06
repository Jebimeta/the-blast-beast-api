package com.theblastbeast.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "blastbeast.site")
public record SiteProperties(
        @NotBlank String name,
        @NotBlank String baseUrl,
        @NotBlank String defaultLocale) {}
