package com.theblastbeast.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.theblastbeast.domain.entity.Article;
import com.theblastbeast.domain.entity.ArticleStatus;
import com.theblastbeast.domain.entity.Category;
import com.theblastbeast.domain.entity.MediaAsset;
import com.theblastbeast.domain.entity.MediaKind;
import com.theblastbeast.domain.entity.User;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

@DisplayName("SEO: sitemap, robots y feeds")
class SeoIT extends AbstractIntegrationTest {

    private static final String BASE = "https://theblastbeast.test";

    @BeforeEach
    void seedContent() {
        User author = seedAdmin();
        Category noticias = seedCategory("noticias", "Noticias");
        seedBand("metallica", "Metallica");
        Article article = seedArticle("metallica-anuncia-gira", "Metallica anuncia gira",
                noticias, author, ArticleStatus.PUBLISHED, OffsetDateTime.now().minusHours(1));

        MediaAsset original = new MediaAsset();
        original.setKind(MediaKind.ORIGINAL);
        original.setStorageKey("2026/01/portada.png");
        original.setMimeType("image/png");
        original.setWidth(1200);
        original.setHeight(800);
        original.setBytes(2048);
        original.setAltText("Portada");
        original = mediaAssetRepository.saveAndFlush(original);

        MediaAsset hero = new MediaAsset();
        hero.setParent(original);
        hero.setKind(MediaKind.HERO);
        hero.setStorageKey("2026/01/portada-hero.webp");
        hero.setMimeType("image/webp");
        hero.setWidth(1200);
        hero.setHeight(800);
        hero.setBytes(1024);
        mediaAssetRepository.saveAndFlush(hero);

        article.setCoverMedia(original);
        articleRepository.saveAndFlush(article);
    }

    @Test
    @DisplayName("el sitemap incluye URLs absolutas, imagen hero y ETag")
    void sitemapConEtag() {
        ResponseEntity<String> response = rest.getForEntity("/sitemap.xml", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_XML))
                .isTrue();
        assertThat(response.getBody())
                .contains("<loc>" + BASE + "/</loc>")
                .contains("<loc>" + BASE + "/articulos/metallica-anuncia-gira</loc>")
                .contains("<loc>" + BASE + "/categorias/noticias</loc>")
                .contains("<loc>" + BASE + "/bandas/metallica</loc>")
                .contains("<image:loc>" + BASE + "/media/2026/01/portada-hero.webp</image:loc>");

        String etag = response.getHeaders().getETag();
        assertThat(etag).isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.setIfNoneMatch(etag);
        ResponseEntity<String> cached = rest.exchange("/sitemap.xml", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(cached.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(cached.getHeaders().getETag()).isEqualTo(etag);
    }

    @Test
    @DisplayName("robots.txt apunta al sitemap y bloquea el panel y la API")
    void robots() {
        ResponseEntity<String> response = rest.getForEntity("/robots.txt", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("Disallow: /admin")
                .contains("Disallow: /api/")
                .contains("Sitemap: " + BASE + "/sitemap.xml");
    }

    @Test
    @DisplayName("el feed global incluye HTML completo y la imagen hero")
    void feedGlobal() {
        ResponseEntity<String> response = rest.getForEntity("/feed.xml", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()
                .isCompatibleWith(MediaType.valueOf("application/rss+xml"))).isTrue();
        assertThat(response.getBody())
                .contains("<title>The Blast Beast</title>")
                .contains("<title>Metallica anuncia gira</title>")
                .contains("<link>" + BASE + "/articulos/metallica-anuncia-gira</link>")
                .contains("<content:encoded><![CDATA[<h2>Metallica anuncia gira</h2>")
                .contains("<media:content url=\"" + BASE + "/media/2026/01/portada-hero.webp\"");
    }

    @Test
    @DisplayName("el feed por categoría existe y el desconocido devuelve 404")
    void feedPorCategoria() {
        ResponseEntity<String> response = rest.getForEntity(
                "/categorias/noticias/feed.xml", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("<title>The Blast Beast — Noticias</title>")
                .contains("<title>Metallica anuncia gira</title>");

        ResponseEntity<ProblemDetail> missing = rest.getForEntity(
                "/categorias/inexistente/feed.xml", ProblemDetail.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody().getProperties()).containsEntry("code", "CAT-001");
    }
}
