package com.theblastbeast.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.theblastbeast.domain.entity.Article;
import com.theblastbeast.domain.entity.ArticleStatus;
import com.theblastbeast.domain.entity.Band;
import com.theblastbeast.domain.entity.Category;
import com.theblastbeast.domain.entity.Tag;
import com.theblastbeast.domain.entity.User;
import com.theblastbeast.domain.vo.BandResponse;
import com.theblastbeast.domain.vo.CategoryAdminResponse;
import com.theblastbeast.domain.vo.TagResponse;
import java.time.OffsetDateTime;
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

@DisplayName("Redacción: taxonomía")
class AdminTaxonomyIT extends AbstractIntegrationTest {

    private User author;

    @BeforeEach
    void prepareAdmin() {
        author = seedAdmin();
    }

    @Test
    @DisplayName("categorías: crea, lista, actualiza y evita slugs duplicados")
    void categoriasCrud() {
        String token = loginAsAdmin();

        CategoryAdminResponse created = createCategory(token, "Noticias");
        assertThat(created.getSlug()).isEqualTo("noticias");
        assertThat(created.getArticleCount()).isZero();

        CategoryAdminResponse duplicate = createCategory(token, "Noticias");
        assertThat(duplicate.getSlug()).isEqualTo("noticias-2");

        ResponseEntity<List<CategoryAdminResponse>> list = rest.exchange(
                "/api/v1/admin/categories",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(list.getBody()).hasSize(2);

        ResponseEntity<CategoryAdminResponse> updated = rest.exchange(
                "/api/v1/admin/categories/" + created.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Actualidad", "slug", "actualidad"),
                        jsonHeaders(token)),
                CategoryAdminResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().getSlug()).isEqualTo("actualidad");

        ResponseEntity<Void> deleted = rest.exchange(
                "/api/v1/admin/categories/" + duplicate.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(jsonHeaders(token)),
                Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("categorías: no borra una categoría con artículos")
    void categoriaEnUso() {
        String token = loginAsAdmin();
        Category category = seedCategory("noticias", "Noticias");
        seedArticle("noticia", "Noticia", category, author, ArticleStatus.DRAFT, null);

        ResponseEntity<ProblemDetail> response = rest.exchange(
                "/api/v1/admin/categories/" + category.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(jsonHeaders(token)),
                ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getProperties()).containsEntry("code", "CAT-003");
    }

    @Test
    @DisplayName("bandas: crea, busca, actualiza y no borra si tiene artículos")
    void bandasCrud() {
        String token = loginAsAdmin();

        BandResponse created = createBand(token, "Metallica");
        assertThat(created.getSlug()).isEqualTo("metallica");
        assertThat(created.getCountry()).isEqualTo("Estados Unidos");
        assertThat(created.getFormedYear()).isEqualTo(1981);

        ResponseEntity<List<BandResponse>> search = rest.exchange(
                "/api/v1/admin/bands?q=metal",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(search.getBody()).hasSize(1);

        ResponseEntity<BandResponse> updated = rest.exchange(
                "/api/v1/admin/bands/" + created.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Metallica", "genre", "Thrash Metal"),
                        jsonHeaders(token)),
                BandResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().getGenre()).isEqualTo("Thrash Metal");

        Article article = seedArticle("noticia", "Noticia", seedCategory("noticias", "Noticias"),
                author, ArticleStatus.DRAFT, null);
        article.getBands().add(bandRepository.findById(created.getId()).orElseThrow());
        articleRepository.saveAndFlush(article);

        ResponseEntity<ProblemDetail> inUse = rest.exchange(
                "/api/v1/admin/bands/" + created.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(jsonHeaders(token)),
                ProblemDetail.class);
        assertThat(inUse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(inUse.getBody().getProperties()).containsEntry("code", "BAND-003");
    }

    @Test
    @DisplayName("bandas: borra una banda sin artículos")
    void bandaSinUso() {
        String token = loginAsAdmin();
        Band band = seedBand("opeth", "Opeth");

        ResponseEntity<Void> deleted = rest.exchange(
                "/api/v1/admin/bands/" + band.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(jsonHeaders(token)),
                Void.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("etiquetas: crea, lista, actualiza y borra")
    void etiquetasCrud() {
        String token = loginAsAdmin();

        TagResponse created = createTag(token, "Gira");
        assertThat(created.getSlug()).isEqualTo("gira");

        TagResponse duplicate = createTag(token, "Gira");
        assertThat(duplicate.getSlug()).isEqualTo("gira-2");

        ResponseEntity<List<TagResponse>> list = rest.exchange(
                "/api/v1/admin/tags?q=gir",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(list.getBody()).hasSize(2);

        ResponseEntity<TagResponse> updated = rest.exchange(
                "/api/v1/admin/tags/" + created.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Giras"), jsonHeaders(token)),
                TagResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().getName()).isEqualTo("Giras");

        ResponseEntity<Void> deleted = rest.exchange(
                "/api/v1/admin/tags/" + duplicate.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(jsonHeaders(token)),
                Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("la taxonomía exige autenticación")
    void requiereAutenticacion() {
        assertThat(rest.getForEntity("/api/v1/admin/categories", ProblemDetail.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.getForEntity("/api/v1/admin/bands", ProblemDetail.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.getForEntity("/api/v1/admin/tags", ProblemDetail.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private CategoryAdminResponse createCategory(String token, String name) {
        ResponseEntity<CategoryAdminResponse> response = rest.exchange(
                "/api/v1/admin/categories",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name), jsonHeaders(token)),
                CategoryAdminResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private BandResponse createBand(String token, String name) {
        ResponseEntity<BandResponse> response = rest.exchange(
                "/api/v1/admin/bands",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "name", name,
                        "country", "Estados Unidos",
                        "formedYear", 1981,
                        "genre", "Heavy Metal"), jsonHeaders(token)),
                BandResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private TagResponse createTag(String token, String name) {
        ResponseEntity<TagResponse> response = rest.exchange(
                "/api/v1/admin/tags",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name), jsonHeaders(token)),
                TagResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }
}
