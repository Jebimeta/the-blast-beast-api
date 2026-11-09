package com.theblastbeast.controller;

import com.theblastbeast.properties.SiteProperties;
import com.theblastbeast.service.FeedService;
import com.theblastbeast.service.SitemapService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recursos XML de infraestructura SEO: sitemap, robots y feeds.
 *
 * <p>No forman parte del contrato OpenAPI JSON. Caddy los publica en la raíz del
 * dominio y los cachea con ETag para no regenerarlos en cada visita.
 */
@RestController
@RequiredArgsConstructor
public class SeoController {

    private static final MediaType RSS =
            MediaType.valueOf("application/rss+xml;charset=UTF-8");
    private static final CacheControl XML_CACHE = CacheControl.maxAge(Duration.ofMinutes(5))
            .cachePublic()
            .sMaxAge(Duration.ofMinutes(10))
            .staleWhileRevalidate(Duration.ofDays(1));
    private static final CacheControl ROBOTS_CACHE =
            CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    private final SitemapService sitemapService;
    private final FeedService feedService;
    private final SiteProperties siteProperties;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return xml(sitemapService.build(), MediaType.APPLICATION_XML, ifNoneMatch);
    }

    @GetMapping(value = "/feed.xml", produces = "application/rss+xml")
    public ResponseEntity<String> feed(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return xml(feedService.globalFeed(), RSS, ifNoneMatch);
    }

    @GetMapping(value = "/categorias/{slug}/feed.xml", produces = "application/rss+xml")
    public ResponseEntity<String> categoryFeed(
            @PathVariable String slug,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return xml(feedService.categoryFeed(slug), RSS, ifNoneMatch);
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots() {
        String body = """
                User-agent: *
                Allow: /
                Disallow: /admin
                Disallow: /api/

                Sitemap: %s/sitemap.xml
                """.formatted(baseUrl());
        return ResponseEntity.ok()
                .cacheControl(ROBOTS_CACHE)
                .contentType(MediaType.TEXT_PLAIN)
                .body(body);
    }

    private ResponseEntity<String> xml(String body, MediaType contentType, String ifNoneMatch) {
        String etag = "\"" + DigestUtils.md5DigestAsHex(body.getBytes(StandardCharsets.UTF_8)) + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(XML_CACHE)
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(XML_CACHE)
                .contentType(contentType)
                .body(body);
    }

    private String baseUrl() {
        String base = siteProperties.baseUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
