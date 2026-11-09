package com.theblastbeast.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.theblastbeast.domain.entity.Article;
import com.theblastbeast.domain.entity.ArticleStatus;
import com.theblastbeast.domain.entity.Band;
import com.theblastbeast.domain.entity.Category;
import com.theblastbeast.domain.entity.Tag;
import com.theblastbeast.domain.entity.User;
import com.theblastbeast.domain.vo.AdminArticleDetail;
import com.theblastbeast.domain.vo.AdminArticlePage;
import com.theblastbeast.domain.vo.ArticleRevisionResponse;
import com.theblastbeast.domain.vo.MarkdownPreviewResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

@DisplayName("Redacción: artículos")
class AdminArticleIT extends AbstractIntegrationTest {

    private User author;
    private Category noticias;
    private Band metallica;
    private Tag gira;

    @BeforeEach
    void seedTaxonomy() {
        author = seedAdmin();
        noticias = seedCategory("noticias", "Noticias");
        metallica = seedBand("metallica", "Metallica");
        gira = seedTag("gira", "Gira");
    }

    @Test
    @DisplayName("crea un borrador con slug, HTML saneado y taxonomía")
    void creaBorrador() {
        AdminArticleDetail created = createArticle("Nueva noticia de prueba");

        assertThat(created.getStatus().name()).isEqualTo("DRAFT");
        assertThat(created.getSlug()).isEqualTo("nueva-noticia-de-prueba");
        assertThat(created.getBodyHtml()).contains("<h2>Nueva noticia de prueba</h2>");
        assertThat(created.getAuthorName()).isEqualTo("Redacción Blast Beast");
        assertThat(created.getCategory().getSlug()).isEqualTo("noticias");
        assertThat(created.getBands()).extracting("slug").containsExactly("metallica");
        assertThat(created.getTags()).extracting("slug").containsExactly("gira");
        assertThat(created.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("valida el contrato y las referencias inexistentes")
    void validaciones() {
        Map<String, Object> invalid = new HashMap<>();
        invalid.put("title", "ab");
        invalid.put("excerpt", "corta");
        invalid.put("bodyMd", "cuerpo");
        invalid.put("categoryId", noticias.getId());

        ResponseEntity<ProblemDetail> validation = rest.exchange(
                "/api/v1/admin/articles",
                HttpMethod.POST,
                new HttpEntity<>(invalid, jsonHeaders(loginAsAdmin())),
                ProblemDetail.class);
        assertThat(validation.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(validation.getBody().getProperties()).containsEntry("code", "GEN-001");
        assertThat(validation.getBody().getProperties()).containsKey("errors");

        Map<String, Object> unknownCategory = validRequest("Categoría inexistente");
        unknownCategory.put("categoryId", 999_999L);

        ResponseEntity<ProblemDetail> missingCategory = rest.exchange(
                "/api/v1/admin/articles",
                HttpMethod.POST,
                new HttpEntity<>(unknownCategory, jsonHeaders(loginAsAdmin())),
                ProblemDetail.class);
        assertThat(missingCategory.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missingCategory.getBody().getProperties()).containsEntry("code", "CAT-001");
    }

    @Test
    @DisplayName("actualizar guarda revisión y permite restaurarla")
    void actualizaYRestauraRevision() {
        String token = loginAsAdmin();
        AdminArticleDetail created = createArticle("Título original");

        Map<String, Object> update = validRequest("Título actualizado");
        update.put("bodyMd", "## Nuevo cuerpo\n\nTexto actualizado.");

        ResponseEntity<AdminArticleDetail> updated = rest.exchange(
                "/api/v1/admin/articles/" + created.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(update, jsonHeaders(token)),
                AdminArticleDetail.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().getTitle()).isEqualTo("Título actualizado");
        assertThat(updated.getBody().getBodyHtml()).contains("Nuevo cuerpo");

        ResponseEntity<List<ArticleRevisionResponse>> revisions = rest.exchange(
                "/api/v1/admin/articles/" + created.getId() + "/revisions",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(revisions.getBody()).hasSize(2);

        ArticleRevisionResponse original = revisions.getBody().stream()
                .filter(revision -> revision.getTitle().equals("Título original"))
                .findFirst()
                .orElseThrow();

        ResponseEntity<AdminArticleDetail> restored = rest.exchange(
                "/api/v1/admin/articles/" + created.getId()
                        + "/revisions/" + original.getId() + "/restore",
                HttpMethod.POST,
                new HttpEntity<>(jsonHeaders(token)),
                AdminArticleDetail.class);

        assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restored.getBody().getTitle()).isEqualTo("Título original");
        assertThat(restored.getBody().getBodyMd()).contains("Título original");
    }

    @Test
    @DisplayName("publica, despublica y archiva con transiciones visibles en la API pública")
    void cicloEditorial() {
        String token = loginAsAdmin();
        AdminArticleDetail created = createArticle("Noticia para publicar");

        ResponseEntity<AdminArticleDetail> published = post(token,
                "/api/v1/admin/articles/" + created.getId() + "/publish");
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(published.getBody().getStatus().name()).isEqualTo("PUBLISHED");
        assertThat(published.getBody().getPublishedAt()).isNotNull();

        assertThat(rest.getForEntity("/api/v1/articles/" + created.getSlug(), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<AdminArticleDetail> unpublished = post(token,
                "/api/v1/admin/articles/" + created.getId() + "/unpublish");
        assertThat(unpublished.getBody().getStatus().name()).isEqualTo("DRAFT");
        assertThat(rest.getForEntity("/api/v1/articles/" + created.getSlug(), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        post(token, "/api/v1/admin/articles/" + created.getId() + "/publish");
        ResponseEntity<AdminArticleDetail> archived = post(token,
                "/api/v1/admin/articles/" + created.getId() + "/archive");
        assertThat(archived.getBody().getStatus().name()).isEqualTo("ARCHIVED");
        assertThat(rest.getForEntity("/api/v1/articles/" + created.getSlug(), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.GONE);
    }

    @Test
    @DisplayName("no publica un artículo sin los campos obligatorios")
    void noPublicaArticuloIncompleto() {
        Article broken = new Article();
        broken.setSlug("articulo-roto");
        broken.setTitle("");
        broken.setExcerpt("");
        broken.setBodyMd("");
        broken.setBodyHtml("");
        broken.setCategory(noticias);
        broken.setAuthor(author);
        broken.setStatus(ArticleStatus.DRAFT);
        broken.setReadingMinutes(0);
        broken = articleRepository.saveAndFlush(broken);

        ResponseEntity<ProblemDetail> response = rest.exchange(
                "/api/v1/admin/articles/" + broken.getId() + "/publish",
                HttpMethod.POST,
                new HttpEntity<>(jsonHeaders(loginAsAdmin())),
                ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties()).containsEntry("code", "ART-005");
    }

    @Test
    @DisplayName("el listado editorial filtra por estado y texto")
    void listadoEditorial() {
        String token = loginAsAdmin();
        AdminArticleDetail draft = createArticle("Entrevista con Opeth");
        AdminArticleDetail toPublish = createArticle("Crónica de festival");
        post(token, "/api/v1/admin/articles/" + toPublish.getId() + "/publish");

        ResponseEntity<AdminArticlePage> drafts = rest.exchange(
                "/api/v1/admin/articles?status=DRAFT",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)),
                AdminArticlePage.class);
        assertThat(drafts.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(drafts.getBody().getContent())
                .extracting("id")
                .containsExactly(draft.getId());

        ResponseEntity<AdminArticlePage> search = rest.exchange(
                "/api/v1/admin/articles?q=opeth",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)),
                AdminArticlePage.class);
        assertThat(search.getBody().getContent()).hasSize(1);
        assertThat(search.getBody().getContent().get(0).getTitle()).contains("Opeth");
    }

    @Test
    @DisplayName("la previsualización usa el mismo saneado que el artículo publicado")
    void previsualizacionSaneada() {
        ResponseEntity<MarkdownPreviewResponse> response = rest.exchange(
                "/api/v1/admin/articles/preview",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("bodyMd", "<script>alert('x')</script>\n\n## Hola\n\nTexto"),
                        jsonHeaders(loginAsAdmin())),
                MarkdownPreviewResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getBodyHtml())
                .doesNotContain("<script>")
                .contains("<h2>Hola</h2>");
    }

    @Test
    @DisplayName("elimina un artículo y devuelve 404 al consultarlo")
    void eliminaArticulo() {
        String token = loginAsAdmin();
        AdminArticleDetail created = createArticle("Artículo efímero");

        ResponseEntity<Void> deleted = rest.exchange(
                "/api/v1/admin/articles/" + created.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(jsonHeaders(token)),
                Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ProblemDetail> missing = rest.exchange(
                "/api/v1/admin/articles/" + created.getId(),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)),
                ProblemDetail.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody().getProperties()).containsEntry("code", "ART-001");
    }

    @Test
    @DisplayName("el panel exige autenticación")
    void requiereAutenticacion() {
        ResponseEntity<ProblemDetail> response = rest.getForEntity(
                "/api/v1/admin/articles", ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private AdminArticleDetail createArticle(String title) {
        ResponseEntity<AdminArticleDetail> response = rest.exchange(
                "/api/v1/admin/articles",
                HttpMethod.POST,
                new HttpEntity<>(validRequest(title), jsonHeaders(loginAsAdmin())),
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
        request.put("bandIds", List.of(metallica.getId()));
        request.put("tagIds", List.of(gira.getId()));
        return request;
    }

    private ResponseEntity<AdminArticleDetail> post(String token, String url) {
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(jsonHeaders(token)),
                AdminArticleDetail.class);
    }
}
