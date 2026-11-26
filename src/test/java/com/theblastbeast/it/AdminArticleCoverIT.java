package com.theblastbeast.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.theblastbeast.domain.entity.Category;
import com.theblastbeast.domain.entity.User;
import com.theblastbeast.domain.vo.AdminArticleDetail;
import com.theblastbeast.domain.vo.MediaSummary;
import com.theblastbeast.domain.vo.MediaVariant;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

@DisplayName("Redacción: portada del artículo")
class AdminArticleCoverIT extends AbstractIntegrationTest {

    private User author;
    private Category noticias;

    @BeforeEach
    void seedContent() {
        author = seedAdmin();
        noticias = seedCategory("noticias", "Noticias");
    }

    @Test
    @DisplayName("sube una portada propia del artículo y genera las variantes")
    void subePortada() {
        String token = loginAsAdmin();
        AdminArticleDetail article = createArticle(token, "Noticia con portada");
        byte[] png = png(1200, 800);

        ResponseEntity<AdminArticleDetail> uploaded = upload(
                token, article.getId(), png, "image/png", "portada.png",
                "Portada de prueba", "Foto: Blast Beast");

        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.OK);
        MediaSummary cover = uploaded.getBody().getCover();
        assertThat(cover).isNotNull();
        assertThat(cover.getUrl()).isNotBlank();
        assertThat(cover.getMimeType()).isEqualTo("image/png");
        assertThat(cover.getWidth()).isEqualTo(1200);
        assertThat(cover.getHeight()).isEqualTo(800);
        assertThat(cover.getAltText()).isEqualTo("Portada de prueba");
        assertThat(cover.getCredit()).isEqualTo("Foto: Blast Beast");
        assertThat(cover.getVariants()).hasSize(2);
        assertThat(cover.getVariants())
                .extracting(variant -> variant.getKind().getValue())
                .containsExactlyInAnyOrder("thumb", "hero");
        assertThat(cover.getVariants())
                .filteredOn(variant -> variant.getKind().getValue().equals("thumb"))
                .singleElement()
                .extracting(MediaVariant::getWidth)
                .isEqualTo(480);

        ResponseEntity<byte[]> file = rest.getForEntity(
                URI.create(cover.getUrl()).getPath(), byte[].class);
        assertThat(file.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(file.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(file.getHeaders().getCacheControl()).contains("max-age=2592000");
        assertThat(file.getBody()).isEqualTo(png);
    }

    @Test
    @DisplayName("reemplazar la portada elimina la imagen anterior")
    void reemplazaPortada() {
        String token = loginAsAdmin();
        AdminArticleDetail article = createArticle(token, "Noticia con portada reemplazable");

        MediaSummary first = uploadCover(token, article.getId()).getBody().getCover();
        assertThat(first).isNotNull();
        String firstPath = URI.create(first.getUrl()).getPath();

        MediaSummary second = uploadCover(token, article.getId(), "Portada nueva", "Crédito nuevo")
                .getBody().getCover();

        assertThat(second).isNotNull();
        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(mediaAssetRepository.findById(first.getId())).isEmpty();
        assertThat(rest.getForEntity(firstPath, ProblemDetail.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("actualiza texto alternativo y crédito sin tocar el resto del artículo")
    void actualizaMetadatos() {
        String token = loginAsAdmin();
        AdminArticleDetail article = createArticle(token, "Noticia con metadatos");
        uploadCover(token, article.getId(), "Alt original", "Crédito original");

        ResponseEntity<AdminArticleDetail> updated = rest.exchange(
                "/api/v1/admin/articles/" + article.getId() + "/cover",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of(
                        "altText", "Alt actualizado",
                        "credit", "Crédito actualizado"), jsonHeaders(token)),
                AdminArticleDetail.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().getTitle()).isEqualTo(article.getTitle());
        assertThat(updated.getBody().getCover().getAltText()).isEqualTo("Alt actualizado");
        assertThat(updated.getBody().getCover().getCredit()).isEqualTo("Crédito actualizado");

        ResponseEntity<AdminArticleDetail> fallback = rest.exchange(
                "/api/v1/admin/articles/" + article.getId() + "/cover",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("altText", "  "), jsonHeaders(token)),
                AdminArticleDetail.class);

        assertThat(fallback.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fallback.getBody().getCover().getAltText()).isEqualTo(article.getTitle());
    }

    @Test
    @DisplayName("quitar la portada borra el original y sus variantes")
    void quitaPortada() {
        String token = loginAsAdmin();
        AdminArticleDetail article = createArticle(token, "Noticia sin portada final");
        MediaSummary cover = uploadCover(token, article.getId()).getBody().getCover();
        String path = URI.create(cover.getUrl()).getPath();

        ResponseEntity<AdminArticleDetail> removed = rest.exchange(
                "/api/v1/admin/articles/" + article.getId() + "/cover",
                HttpMethod.DELETE,
                new HttpEntity<>(jsonHeaders(token)),
                AdminArticleDetail.class);

        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(removed.getBody().getCover()).isNull();
        assertThat(mediaAssetRepository.count()).isZero();
        assertThat(rest.getForEntity(path, ProblemDetail.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("eliminar el artículo limpia también su portada")
    void eliminaPortadaConArticulo() {
        String token = loginAsAdmin();
        AdminArticleDetail article = createArticle(token, "Artículo efímero con portada");
        MediaSummary cover = uploadCover(token, article.getId()).getBody().getCover();
        String path = URI.create(cover.getUrl()).getPath();

        ResponseEntity<Void> deleted = rest.exchange(
                "/api/v1/admin/articles/" + article.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(jsonHeaders(token)),
                Void.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(mediaAssetRepository.count()).isZero();
        assertThat(rest.getForEntity(path, ProblemDetail.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("publicar exige una portada")
    void publicarExigePortada() {
        String token = loginAsAdmin();
        AdminArticleDetail article = createArticle(token, "Noticia que necesita portada");

        ResponseEntity<ProblemDetail> rejected = rest.exchange(
                "/api/v1/admin/articles/" + article.getId() + "/publish",
                HttpMethod.POST,
                new HttpEntity<>(jsonHeaders(token)),
                ProblemDetail.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody().getProperties()).containsEntry("code", "ART-005");
        assertThat(rejected.getBody().getDetail()).contains("portada");

        uploadCover(token, article.getId());

        ResponseEntity<AdminArticleDetail> published = rest.exchange(
                "/api/v1/admin/articles/" + article.getId() + "/publish",
                HttpMethod.POST,
                new HttpEntity<>(jsonHeaders(token)),
                AdminArticleDetail.class);
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(published.getBody().getStatus().name()).isEqualTo("PUBLISHED");
        assertThat(published.getBody().getCover()).isNotNull();
    }

    @Test
    @DisplayName("rechaza archivos vacíos y formatos no admitidos")
    void validaArchivos() {
        String token = loginAsAdmin();
        AdminArticleDetail article = createArticle(token, "Noticia con archivos inválidos");

        ResponseEntity<ProblemDetail> empty = uploadProblem(
                token, article.getId(), new byte[0], "image/png", "vacia.png");
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(empty.getBody().getProperties()).containsEntry("code", "GEN-001");

        ResponseEntity<ProblemDetail> unsupported = uploadProblem(
                token, article.getId(), "texto plano".getBytes(), "text/plain", "nota.txt");
        assertThat(unsupported.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(unsupported.getBody().getProperties()).containsEntry("code", "MED-001");
    }

    @Test
    @DisplayName("la portada exige autenticación")
    void requiereAutenticacion() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<ProblemDetail> response = rest.exchange(
                "/api/v1/admin/articles/1/cover",
                HttpMethod.PUT,
                new HttpEntity<>(multipartCover(
                        png(32, 32), "image/png", "portada.png", null, null), headers),
                ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private AdminArticleDetail createArticle(String token, String title) {
        ResponseEntity<AdminArticleDetail> response = rest.exchange(
                "/api/v1/admin/articles",
                HttpMethod.POST,
                new HttpEntity<>(validRequest(title), jsonHeaders(token)),
                AdminArticleDetail.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private Map<String, Object> validRequest(String title) {
        Map<String, Object> request = new HashMap<>();
        request.put("title", title);
        request.put("excerpt", "Entradilla de " + title);
        request.put("bodyMd", "## " + title + "\n\nContenido de la noticia.");
        request.put("categoryId", noticias.getId());
        return request;
    }

    private ResponseEntity<AdminArticleDetail> upload(String token, Long articleId, byte[] content,
            String contentType, String filename, String altText, String credit) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(
                "/api/v1/admin/articles/" + articleId + "/cover",
                HttpMethod.PUT,
                new HttpEntity<>(multipartCover(
                        content, contentType, filename, altText, credit), headers),
                AdminArticleDetail.class);
    }

    private ResponseEntity<ProblemDetail> uploadProblem(String token, Long articleId, byte[] content,
            String contentType, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(
                "/api/v1/admin/articles/" + articleId + "/cover",
                HttpMethod.PUT,
                new HttpEntity<>(multipartCover(
                        content, contentType, filename, null, null), headers),
                ProblemDetail.class);
    }
}
