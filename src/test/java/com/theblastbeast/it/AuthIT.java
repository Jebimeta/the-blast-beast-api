package com.theblastbeast.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.theblastbeast.domain.entity.Role;
import com.theblastbeast.domain.entity.UserStatus;
import com.theblastbeast.domain.vo.AuthResponse;
import com.theblastbeast.domain.vo.AuthUser;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

@DisplayName("Autenticación")
class AuthIT extends AbstractIntegrationTest {

    @BeforeEach
    void prepareAdmin() {
        seedAdmin();
    }

    @Test
    @DisplayName("login devuelve access token y cookie HttpOnly de refresh")
    void loginCorrecto() {
        ResponseEntity<AuthResponse> response = rest.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD),
                AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getAccessToken()).isNotBlank();
        assertThat(body.getTokenType()).isEqualTo("Bearer");
        assertThat(body.getExpiresIn()).isPositive();
        assertThat(body.getUser().getEmail()).isEqualTo(ADMIN_EMAIL);
        assertThat(body.getUser().getRole().name()).isEqualTo("ADMIN");

        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie)
                .contains("bb_refresh=")
                .contains("HttpOnly")
                .contains("Path=/api/v1/auth")
                .contains("SameSite=Lax");
    }

    @Test
    @DisplayName("credenciales incorrectas devuelven 401")
    void credencialesIncorrectas() {
        ResponseEntity<ProblemDetail> response = rest.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", ADMIN_EMAIL, "password", "contraseña-incorrecta"),
                ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getProperties()).containsEntry("code", "AUTH-001");
    }

    @Test
    @DisplayName("un usuario deshabilitado no puede entrar")
    void usuarioDeshabilitado() {
        seedUser("baja@theblastbeast.com", "Baja2026!", Role.EDITOR, UserStatus.DISABLED);

        ResponseEntity<ProblemDetail> response = rest.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", "baja@theblastbeast.com", "password", "Baja2026!"),
                ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getProperties()).containsEntry("code", "AUTH-006");
    }

    @Test
    @DisplayName("me exige token y devuelve el usuario autenticado")
    void meConToken() {
        String token = loginAsAdmin();

        ResponseEntity<AuthUser> response = rest.exchange(
                "/api/v1/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)),
                AuthUser.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEmail()).isEqualTo(ADMIN_EMAIL);

        ResponseEntity<ProblemDetail> anonymous = rest.getForEntity(
                "/api/v1/auth/me", ProblemDetail.class);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("refresh rota el token y detecta la reutilización revocando la familia")
    void refreshRotaYDetectaReuso() {
        ResponseEntity<AuthResponse> login = rest.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD),
                AuthResponse.class);
        String oldCookie = refreshCookie(login);

        ResponseEntity<AuthResponse> refresh = rest.exchange(
                "/api/v1/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(cookieHeaders(oldCookie)),
                AuthResponse.class);

        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refresh.getBody().getAccessToken()).isNotBlank();
        String newCookie = refreshCookie(refresh);
        assertThat(newCookie).isNotEqualTo(oldCookie);

        // Reutilizar el token rotado invalida toda la familia.
        ResponseEntity<ProblemDetail> reuse = rest.exchange(
                "/api/v1/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(cookieHeaders(oldCookie)),
                ProblemDetail.class);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reuse.getBody().getProperties()).containsEntry("code", "AUTH-005");

        // El token nuevo emitido en la rotación también queda revocado.
        ResponseEntity<ProblemDetail> afterReuse = rest.exchange(
                "/api/v1/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(cookieHeaders(newCookie)),
                ProblemDetail.class);
        assertThat(afterReuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("refresh sin cookie devuelve 401")
    void refreshSinCookie() {
        ResponseEntity<ProblemDetail> response = rest.exchange(
                "/api/v1/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(new HttpHeaders()),
                ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getProperties()).containsEntry("code", "AUTH-005");
    }

    @Test
    @DisplayName("logout revoca el refresh y limpia la cookie")
    void logoutInvalidaSesion() {
        ResponseEntity<AuthResponse> login = rest.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD),
                AuthResponse.class);
        String cookie = refreshCookie(login);

        ResponseEntity<Void> logout = rest.exchange(
                "/api/v1/auth/logout",
                HttpMethod.POST,
                new HttpEntity<>(cookieHeaders(cookie)),
                Void.class);

        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(logout.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("bb_refresh=")
                .contains("Max-Age=0");

        ResponseEntity<ProblemDetail> refresh = rest.exchange(
                "/api/v1/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(cookieHeaders(cookie)),
                ProblemDetail.class);
        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
