package com.theblastbeast.properties;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Orígenes autorizados para las peticiones CORS contra la API.
 *
 * <p>Si no se configura nada se usa el origen público del sitio
 * ({@code blastbeast.site.base-url}). Cada origen se normaliza a
 * {@code esquema://host[:puerto]}, sin barra final ni puerto por defecto, y se
 * valida al arrancar: un valor mal escrito debe romper el arranque, no dejar
 * la API aceptando orígenes que no tocan.
 */
@ConfigurationProperties(prefix = "blastbeast.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    /**
     * Orígenes efectivos: los configurados o, si la lista queda vacía, el
     * origen público del sitio.
     */
    public List<String> effectiveOrigins(String fallbackOrigin) {
        List<String> configured = allowedOrigins.stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .map(String::trim)
                .map(origin -> origin.replaceAll("/+$", ""))
                .map(CorsProperties::normalize)
                .distinct()
                .toList();
        if (!configured.isEmpty()) {
            return configured;
        }
        return List.of(normalize(fallbackOrigin.trim().replaceAll("/+$", "")));
    }

    private static String normalize(String origin) {
        if ("*".equals(origin)) {
            throw new IllegalArgumentException(
                    "blastbeast.cors.allowed-origins no admite '*': la API envía credenciales");
        }
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Origen CORS inválido: " + origin, ex);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean http = "http".equals(scheme);
        boolean https = "https".equals(scheme);
        int port = uri.getPort();
        boolean invalid = (!http && !https)
                || uri.getHost() == null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || uri.getUserInfo() != null
                || port == 0
                || port > 65535;
        if (invalid) {
            throw new IllegalArgumentException(
                    "Origen CORS inválido (se esperaba http(s)://host[:puerto]): " + origin);
        }
        boolean defaultPort = (http && port == 80) || (https && port == 443);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return scheme + "://" + host + (port == -1 || defaultPort ? "" : ":" + port);
    }
}
