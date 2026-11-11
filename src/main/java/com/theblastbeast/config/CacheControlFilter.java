package com.theblastbeast.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Política de caché de la API.
 *
 * <p>El contenido público (artículos, categorías, búsqueda, ping) se puede cachear
 * durante un minuto: además de aliviar la API, permite que el transfer cache de la
 * SSR reutilice las respuestas en la hidratación. Todo lo privado (auth y panel de
 * redacción) se marca como {@code no-store}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CacheControlFilter extends OncePerRequestFilter {

    private static final String PUBLIC_CACHE = "public, max-age=60, stale-while-revalidate=30";
    private static final String PRIVATE_CACHE = "no-store";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        if (path.startsWith("/api/v1/")) {
            boolean publicGet = "GET".equals(request.getMethod())
                    && !path.startsWith("/api/v1/admin/")
                    && !path.startsWith("/api/v1/auth/");
            response.setHeader(HttpHeaders.CACHE_CONTROL, publicGet ? PUBLIC_CACHE : PRIVATE_CACHE);
        }

        filterChain.doFilter(request, response);
    }
}
