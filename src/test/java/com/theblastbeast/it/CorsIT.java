package com.theblastbeast.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("CORS de la API")
class CorsIT extends AbstractIntegrationTest {

    private static final String ORIGIN = "https://theblastbeast.test";
    private static final String FOREIGN_ORIGIN = "https://evil.example";

    @Test
    @DisplayName("el preflight del origen del sitio recibe los permisos")
    void preflightOrigenPermitido() {
        ResponseEntity<String> response = preflight(ORIGIN, "GET");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(ORIGIN);
        assertThat(response.getHeaders().getAccessControlAllowCredentials()).isTrue();
        assertThat(response.getHeaders().getAccessControlAllowMethods())
                .contains(HttpMethod.GET, HttpMethod.POST, HttpMethod.PATCH, HttpMethod.DELETE);
        assertThat(response.getHeaders().getAccessControlAllowHeaders()
                .stream().map(String::toLowerCase).toList())
                .contains("authorization", "content-type", "if-none-match");
        assertThat(response.getHeaders().getAccessControlMaxAge()).isEqualTo(3600L);
        assertThat(response.getHeaders().getVary()).contains(HttpHeaders.ORIGIN);
    }

    @Test
    @DisplayName("el preflight de un origen ajeno se rechaza sin cabeceras CORS")
    void preflightOrigenAjeno() {
        ResponseEntity<String> response = preflight(FOREIGN_ORIGIN, "GET");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    @Test
    @DisplayName("la petición real del origen del sitio lleva Allow-Origin y credenciales")
    void peticionRealOrigenPermitido() {
        ResponseEntity<String> response = get("/api/v1/ping", ORIGIN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(ORIGIN);
        assertThat(response.getHeaders().getAccessControlAllowCredentials()).isTrue();
    }

    @Test
    @DisplayName("la petición real de un origen ajeno se rechaza sin permisos CORS")
    void peticionRealOrigenAjeno() {
        ResponseEntity<String> response = get("/api/v1/ping", FOREIGN_ORIGIN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    private ResponseEntity<String> preflight(String origin, String method) {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(origin);
        headers.setAccessControlRequestMethod(HttpMethod.valueOf(method));
        headers.setAccessControlRequestHeaders(
                List.of("Authorization", "Content-Type", "If-None-Match"));
        return rest.exchange("/api/v1/articles", HttpMethod.OPTIONS,
                new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> get(String path, String origin) {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(origin);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
