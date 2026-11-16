package com.theblastbeast.service.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theblastbeast.properties.SocialSummaryProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("Cliente de IA para resúmenes sociales")
class OpenAiSocialSummaryClientTest {

    private static final SocialSummaryContext CONTEXT = new SocialSummaryContext(
            "Metallica anuncia gira",
            "La banda confirma fechas.",
            "## Gira\n\nTexto.",
            "Noticias",
            List.of("Metallica"),
            List.of("Gira"),
            "https://theblastbeast.test/articulos/metallica-anuncia-gira");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockRestServiceServer server;
    private RestClient.Builder builder;
    private OpenAiSocialSummaryClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("https://ai.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiSocialSummaryClient(
                builder.build(),
                new SocialSummaryProperties(
                        "https://ai.test", "test-key", "gpt-test", Duration.ofSeconds(5)),
                objectMapper);
    }

    @Test
    @DisplayName("parsea el JSON del proveedor aunque venga entre cercas Markdown")
    void parseaRespuesta() throws Exception {
        String content = """
                ```json
                {"x":"Texto para X","facebook":"Texto para Facebook","instagram":"Texto para Instagram","whatsapp":"Texto para WhatsApp"}
                ```
                """;
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andRespond(withSuccess(chatResponse(content), MediaType.APPLICATION_JSON));

        Optional<SocialSummaryDraft> draft = client.generate(CONTEXT);

        assertThat(draft).isPresent();
        assertThat(draft.get().x()).isEqualTo("Texto para X");
        assertThat(draft.get().whatsapp()).isEqualTo("Texto para WhatsApp");
        server.verify();
    }

    @Test
    @DisplayName("devuelve vacío si el proveedor falla")
    void proveedorCaido() {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withServerError());

        assertThat(client.generate(CONTEXT)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("no llama al proveedor si no hay clave configurada")
    void sinClave() {
        OpenAiSocialSummaryClient withoutKey = new OpenAiSocialSummaryClient(
                builder.build(),
                new SocialSummaryProperties(
                        "https://ai.test", " ", "gpt-test", Duration.ofSeconds(5)),
                objectMapper);

        assertThat(withoutKey.generate(CONTEXT)).isEmpty();
        server.verify();
    }

    private String chatResponse(String content) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", content)))));
    }
}
