package com.theblastbeast.config;

import com.theblastbeast.domain.vo.ArticleSort;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring convierte los enums de query string con {@code Enum.valueOf}, que distingue
 * mayúsculas. El contrato usa valores en minúsculas ({@code latest}), así que se registra
 * el conversor que genera OpenAPI.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, ArticleSort.class, ArticleSort::fromValue);
    }
}
