package com.theblastbeast.service.social;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theblastbeast.properties.SocialSummaryProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente para APIs de chat compatibles con OpenAI. Cualquier proveedor que
 * exponga {@code POST /chat/completions} sirve cambiando la URL y el modelo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiSocialSummaryClient implements SocialSummaryClient {

    private static final int MAX_BODY_CHARS = 6_000;

    private static final String SYSTEM_PROMPT = """
            Eres el editor de redes sociales de The Blast Beast, un medio de noticias
            de heavy metal en español. Redacta textos breves y periodísticos a partir
            del artículo. Reglas:
            - No inventes datos, cifras, fechas, citas ni nombres que no aparezcan en el texto.
            - Escribe en español de España.
            - Incluye siempre la URL exacta que se te proporciona.
            - X: máximo 280 caracteres, tono directo y sin hashtags.
            - Facebook: dos o tres frases, máximo 600 caracteres.
            - Instagram: pie de foto de dos o tres frases con tres o cuatro hashtags al final,
              máximo 800 caracteres.
            - WhatsApp: una o dos líneas, máximo 400 caracteres.
            - Devuelve SOLO un objeto JSON válido con las claves "x", "facebook",
              "instagram" y "whatsapp". Sin markdown ni explicaciones.
            """;

    private final RestClient socialSummaryRestClient;
    private final SocialSummaryProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<SocialSummaryDraft> generate(SocialSummaryContext context) {
        if (!properties.configured()) {
            log.debug("Resumen social sin IA: AI_API_KEY no configurada");
            return Optional.empty();
        }

        try {
            Map<String, Object> request = Map.of(
                    "model", properties.model(),
                    "temperature", 0.4,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", userPrompt(context))));

            JsonNode response = socialSummaryRestClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);

            String content = response == null
                    ? null
                    : response.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                log.warn("El proveedor de IA devolvió una respuesta vacía");
                return Optional.empty();
            }

            SocialSummaryDraft draft = objectMapper.readValue(stripFence(content),
                    SocialSummaryDraft.class);
            return draft.isComplete() ? Optional.of(draft) : Optional.empty();
        } catch (RestClientException | JsonProcessingException exception) {
            log.warn("No se pudo generar el resumen social con IA: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private String userPrompt(SocialSummaryContext context) {
        return """
                Titular: %s
                Entradilla: %s
                Categoría: %s
                Bandas: %s
                Etiquetas: %s
                URL: %s

                Cuerpo del artículo:
                %s
                """.formatted(
                context.title(),
                context.excerpt(),
                context.category(),
                String.join(", ", context.bands()),
                String.join(", ", context.tags()),
                context.url(),
                truncate(context.bodyMd(), MAX_BODY_CHARS));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String text = value.strip();
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private static String stripFence(String content) {
        String value = content.strip();
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            if (firstNewline >= 0) {
                value = value.substring(firstNewline + 1);
            }
            if (value.endsWith("```")) {
                value = value.substring(0, value.length() - 3);
            }
        }
        return value.strip();
    }
}
