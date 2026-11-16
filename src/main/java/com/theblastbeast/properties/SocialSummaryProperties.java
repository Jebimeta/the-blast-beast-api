package com.theblastbeast.properties;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración del proveedor de IA para los resúmenes de redes sociales.
 * Es compatible con cualquier API de chat estilo OpenAI; sin clave se usa la
 * plantilla determinista y el contenido no sale del servidor.
 */
@Validated
@ConfigurationProperties(prefix = "blastbeast.ai")
public record SocialSummaryProperties(
        @NotBlank String baseUrl,
        String apiKey,
        @NotBlank String model,
        Duration timeout) {

    public SocialSummaryProperties {
        if (timeout == null) {
            timeout = Duration.ofSeconds(20);
        }
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
