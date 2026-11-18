package com.theblastbeast.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Orígenes CORS")
class CorsPropertiesTest {

    private static final String FALLBACK = "https://theblastbeast.test";

    @Test
    @DisplayName("sin configuración usa el origen público del sitio")
    void usaElOrigenDelSitio() {
        assertThat(new CorsProperties(null).effectiveOrigins(FALLBACK))
                .containsExactly(FALLBACK);
        assertThat(new CorsProperties(List.of()).effectiveOrigins(FALLBACK + "/"))
                .containsExactly(FALLBACK);
    }

    @Test
    @DisplayName("normaliza espacios, mayúsculas y barra final")
    void normalizaOrigenes() {
        CorsProperties properties = new CorsProperties(List.of(
                "  HTTPS://TheBlastBeast.COM/  ", "http://localhost:4200/"));

        assertThat(properties.effectiveOrigins(FALLBACK))
                .containsExactly("https://theblastbeast.com", "http://localhost:4200");
    }

    @Test
    @DisplayName("omite el puerto por defecto del esquema")
    void omitePuertoPorDefecto() {
        CorsProperties properties = new CorsProperties(List.of(
                "https://theblastbeast.com:443", "http://theblastbeast.com:80"));

        assertThat(properties.effectiveOrigins(FALLBACK))
                .containsExactly("https://theblastbeast.com", "http://theblastbeast.com");
    }

    @Test
    @DisplayName("elimina duplicados y entradas vacías")
    void eliminaDuplicados() {
        CorsProperties properties = new CorsProperties(List.of(
                "https://theblastbeast.com", "https://theblastbeast.com/", " "));

        assertThat(properties.effectiveOrigins(FALLBACK))
                .containsExactly("https://theblastbeast.com");
    }

    @Test
    @DisplayName("rechaza el comodín")
    void rechazaComodin() {
        assertThatThrownBy(() -> new CorsProperties(List.of("*")).effectiveOrigins(FALLBACK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'*'");
    }

    @Test
    @DisplayName("rechaza rutas, credenciales y esquemas no HTTP")
    void rechazaOrigenesInvalidos() {
        for (String origin : List.of(
                "https://theblastbeast.com/api",
                "ftp://theblastbeast.com",
                "https://user:pass@theblastbeast.com",
                "theblastbeast.com",
                "https://theblastbeast.com?debug=1")) {
            assertThatThrownBy(() -> new CorsProperties(List.of(origin)).effectiveOrigins(FALLBACK))
                    .as(origin)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Origen CORS inválido");
        }
    }
}
