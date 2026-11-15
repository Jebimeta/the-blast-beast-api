package com.theblastbeast.integration.ticketmaster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.theblastbeast.properties.TicketmasterProperties;
import com.theblastbeast.util.MadridTime;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("Cliente de Ticketmaster")
class TicketmasterClientImplTest {

    private static final String BASE_URL = "https://tm.test/discovery/v2";
    private static final String EVENTS_JSON = """
            {
              "page": {"size": 200, "totalElements": 1, "totalPages": 1, "number": 0},
              "_embedded": {
                "events": [
                  {"id": "evt-1", "name": "Metallica", "url": "https://tm.test/evt-1"}
                ]
              }
            }
            """;

    private MockRestServiceServer server;
    private RestClient.Builder builder;
    private TicketmasterProperties properties;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        properties = properties("test-key", 3);
    }

    @Test
    @DisplayName("consulta una ventana con los filtros de España y música")
    void consultaVentana() {
        LocalDate from = LocalDate.of(2026, 6, 20);
        LocalDate to = LocalDate.of(2026, 6, 27);
        server.expect(requestTo(startsWith(BASE_URL + "/events.json")))
                .andExpect(queryParam("apikey", "test-key"))
                .andExpect(queryParam("countryCode", "ES"))
                .andExpect(queryParam("segmentId", "KZFzniwnSyZfZ7v7nJ"))
                .andExpect(queryParam("locale", "es"))
                .andExpect(queryParam("sort", "date,asc"))
                .andExpect(queryParam("size", "200"))
                .andExpect(queryParam("page", "0"))
                .andExpect(queryParam("startDateTime", MadridTime.ticketmasterStart(from)))
                .andExpect(queryParam("endDateTime", MadridTime.ticketmasterEndExclusive(to)))
                .andRespond(withSuccess(EVENTS_JSON, MediaType.APPLICATION_JSON));

        TicketmasterEventPage page = client().search(new TicketmasterQuery(from, to, 0, 500));

        assertThat(page.events()).singleElement()
                .satisfies(event -> assertThat(event.id()).isEqualTo("evt-1"));
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.totalElements()).isEqualTo(1);
        server.verify();
    }

    @Test
    @DisplayName("reintenta un 429 y devuelve la respuesta siguiente")
    void reintentaErroresTransitorios() {
        server.expect(requestTo(startsWith(BASE_URL + "/events.json")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(startsWith(BASE_URL + "/events.json")))
                .andRespond(withSuccess(EVENTS_JSON, MediaType.APPLICATION_JSON));

        TicketmasterEventPage page = client().search(query());

        assertThat(page.events()).hasSize(1);
        server.verify();
    }

    @Test
    @DisplayName("agota los reintentos y lanza una excepción de integración")
    void agotaReintentos() {
        properties = properties("test-key", 2);
        server.expect(requestTo(startsWith(BASE_URL + "/events.json")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo(startsWith(BASE_URL + "/events.json")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client().search(query()))
                .isInstanceOf(TicketmasterException.class);
        server.verify();
    }

    @Test
    @DisplayName("falla sin clave antes de hacer ninguna petición")
    void fallaSinClave() {
        properties = properties("", 3);

        assertThatThrownBy(() -> client().search(query()))
                .isInstanceOf(TicketmasterException.class)
                .hasMessageContaining("TICKETMASTER_API_KEY");
    }

    @Test
    @DisplayName("tolera una respuesta sin eventos")
    void respuestaVacia() {
        server.expect(requestTo(startsWith(BASE_URL + "/events.json")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        TicketmasterEventPage page = client().search(query());

        assertThat(page.events()).isEmpty();
        assertThat(page.totalPages()).isZero();
        assertThat(page.totalElements()).isZero();
        server.verify();
    }

    private TicketmasterClientImpl client() {
        return new TicketmasterClientImpl(properties, builder);
    }

    private TicketmasterQuery query() {
        return new TicketmasterQuery(LocalDate.of(2026, 6, 20), LocalDate.of(2026, 6, 27), 0, 200);
    }

    private TicketmasterProperties properties(String apiKey, int maxAttempts) {
        return new TicketmasterProperties(true, apiKey, BASE_URL, "ES", "es",
                "0 0 */6 * * *", 180, 7, 200, 5, maxAttempts, Duration.ZERO);
    }
}
