package com.theblastbeast.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.theblastbeast.domain.vo.PressContact;
import com.theblastbeast.domain.vo.PressContactList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

@DisplayName("Redacción: contactos de prensa")
@TestPropertySource(properties = "blastbeast.press-contacts.csv-path=src/test/resources/press/contactos-prensa-e-imagenes.csv")
class AdminPressContactsIT extends AbstractIntegrationTest {

    @BeforeEach
    void prepareAdmin() {
        seedAdmin();
    }

    @Test
    @DisplayName("exige sesión y devuelve el CSV a la redacción")
    void exigeSesionYDevuelveContactos() {
        ResponseEntity<ProblemDetail> unauthorized = rest.getForEntity(
                "/api/v1/admin/press-contacts", ProblemDetail.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String token = loginAsAdmin();
        ResponseEntity<PressContactList> response = rest.exchange(
                "/api/v1/admin/press-contacts",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)),
                PressContactList.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PressContactList body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTotal()).isEqualTo(2);
        assertThat(body.getUpdatedAt()).isNotNull();
        assertThat(body.getRows())
                .extracting(PressContact::getNombre)
                .containsExactly("Frontiers Music", "Leyendas del Rock");
        assertThat(body.getRows().get(0).getNotas())
                .isEqualTo("Directorio abierto, con comas y \"comillas\".");
    }
}
