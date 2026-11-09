package com.theblastbeast.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.theblastbeast.domain.entity.Article;
import com.theblastbeast.domain.entity.ArticleStatus;
import com.theblastbeast.domain.entity.Band;
import com.theblastbeast.domain.entity.Category;
import com.theblastbeast.domain.entity.Tag;
import com.theblastbeast.domain.entity.User;
import com.theblastbeast.domain.vo.ArticleDetail;
import com.theblastbeast.domain.vo.ArticlePage;
import com.theblastbeast.domain.vo.ArticleSummary;
import com.theblastbeast.domain.vo.BandDetail;
import com.theblastbeast.domain.vo.CategoryResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

@DisplayName("API pública")
class PublicApiIT extends AbstractIntegrationTest {

    private User author;
    private Category noticias;
    private Category conciertos;
    private Band metallica;
    private Tag gira;

    @BeforeEach
    void seedContent() {
        author = seedAdmin();
        noticias = seedCategory("noticias", "Noticias");
        conciertos = seedCategory("conciertos", "Conciertos");
        metallica = seedBand("metallica", "Metallica");
        gira = seedTag("gira", "Gira");

        Article lead = seedArticle("metallica-anuncia-gira", "Metallica anuncia gira",
                noticias, author, ArticleStatus.PUBLISHED, OffsetDateTime.now().minusHours(2));
        lead.getBands().add(metallica);
        lead.getTags().add(gira);
        articleRepository.saveAndFlush(lead);

        seedArticle("opeth-anuncia-disco", "Opeth anuncia nuevo disco",
                noticias, author, ArticleStatus.PUBLISHED, OffsetDateTime.now().minusDays(1));
        seedArticle("borrador-interno", "Borrador interno",
                noticias, author, ArticleStatus.DRAFT, null);
        seedArticle("noticia-archivada", "Noticia archivada",
                conciertos, author, ArticleStatus.ARCHIVED, OffsetDateTime.now().minusDays(30));
    }

    @Test
    @DisplayName("el listado solo devuelve publicados y pagina los resultados")
    void listadoSoloDevuelvePublicados() {
        ResponseEntity<ArticlePage> response = rest.getForEntity(
                "/api/v1/articles?size=1", ArticlePage.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArticlePage page = response.getBody();
        assertThat(page).isNotNull();
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("Metallica anuncia gira");
        assertThat(page.getFirst()).isTrue();
        assertThat(page.getLast()).isFalse();
    }

    @Test
    @DisplayName("filtra por categoría, etiqueta y banda")
    void filtraPorTaxonomia() {
        assertThat(list("/api/v1/articles?category=noticias").getTotalElements()).isEqualTo(2);
        assertThat(list("/api/v1/articles?category=conciertos").getTotalElements()).isZero();
        assertThat(list("/api/v1/articles?tag=gira").getTotalElements()).isEqualTo(1);
        assertThat(list("/api/v1/articles?band=metallica").getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("busca en español y ordena por antigüedad")
    void buscaYOrdena() {
        assertThat(list("/api/v1/articles?q=metallica").getTotalElements()).isEqualTo(1);

        ArticlePage oldest = list("/api/v1/articles?sort=oldest");
        assertThat(oldest.getContent().get(0).getSlug()).isEqualTo("opeth-anuncia-disco");
    }

    @Test
    @DisplayName("los destacados llegan ordenados del más reciente al más antiguo")
    void destacadosOrdenados() {
        ResponseEntity<List<ArticleSummary>> response = rest.exchange(
                "/api/v1/articles/featured",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody().get(0).getSlug()).isEqualTo("metallica-anuncia-gira");
    }

    @Test
    @DisplayName("el detalle incluye cuerpo, autor, bandas y etiquetas")
    void detalleCompleto() {
        ResponseEntity<ArticleDetail> response = rest.getForEntity(
                "/api/v1/articles/metallica-anuncia-gira", ArticleDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArticleDetail detail = response.getBody();
        assertThat(detail).isNotNull();
        assertThat(detail.getTitle()).isEqualTo("Metallica anuncia gira");
        assertThat(detail.getBodyHtml()).contains("Contenido de la noticia");
        assertThat(detail.getCategory().getSlug()).isEqualTo("noticias");
        assertThat(detail.getAuthorName()).isEqualTo("Redacción Blast Beast");
        assertThat(detail.getBands()).extracting("slug").containsExactly("metallica");
        assertThat(detail.getTags()).extracting("slug").containsExactly("gira");
    }

    @Test
    @DisplayName("borrador devuelve 404 y archivado devuelve 410")
    void estadosNoPublicados() {
        ResponseEntity<ProblemDetail> draft = rest.getForEntity(
                "/api/v1/articles/borrador-interno", ProblemDetail.class);
        assertThat(draft.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(draft.getBody().getProperties()).containsEntry("code", "ART-001");

        ResponseEntity<ProblemDetail> archived = rest.getForEntity(
                "/api/v1/articles/noticia-archivada", ProblemDetail.class);
        assertThat(archived.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(archived.getBody().getProperties()).containsEntry("code", "ART-003");
    }

    @Test
    @DisplayName("las categorías incluyen el número de publicados")
    void categoriasConConteo() {
        ResponseEntity<List<CategoryResponse>> response = rest.exchange(
                "/api/v1/categories",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody())
                .filteredOn(category -> category.getSlug().equals("noticias"))
                .singleElement()
                .extracting(CategoryResponse::getArticleCount)
                .isEqualTo(2L);
        assertThat(response.getBody())
                .filteredOn(category -> category.getSlug().equals("conciertos"))
                .singleElement()
                .extracting(CategoryResponse::getArticleCount)
                .isEqualTo(0L);
    }

    @Test
    @DisplayName("la ficha de banda incluye sus artículos publicados")
    void fichaDeBanda() {
        ResponseEntity<BandDetail> response = rest.getForEntity(
                "/api/v1/bands/metallica", BandDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Metallica");
        assertThat(response.getBody().getArticles()).hasSize(1);
        assertThat(response.getBody().getArticles().get(0).getSlug())
                .isEqualTo("metallica-anuncia-gira");

        ResponseEntity<ProblemDetail> missing = rest.getForEntity(
                "/api/v1/bands/no-existe", ProblemDetail.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody().getProperties()).containsEntry("code", "BAND-001");
    }

    @Test
    @DisplayName("la búsqueda exige al menos dos caracteres")
    void busquedaValidada() {
        assertThat(list("/api/v1/search?q=metallica").getTotalElements()).isEqualTo(1);

        ResponseEntity<ProblemDetail> tooShort = rest.getForEntity(
                "/api/v1/search?q=a", ProblemDetail.class);
        assertThat(tooShort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("ping responde sin autenticación")
    void pingPublico() {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/ping", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    private ArticlePage list(String url) {
        ResponseEntity<ArticlePage> response = rest.getForEntity(url, ArticlePage.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }
}
