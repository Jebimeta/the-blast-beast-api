package com.theblastbeast.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.theblastbeast.domain.entity.Article;
import com.theblastbeast.domain.entity.ArticleStatus;
import com.theblastbeast.domain.entity.Band;
import com.theblastbeast.domain.entity.Category;
import com.theblastbeast.domain.entity.Role;
import com.theblastbeast.domain.entity.Tag;
import com.theblastbeast.domain.entity.User;
import com.theblastbeast.domain.entity.UserStatus;
import com.theblastbeast.domain.vo.AuthResponse;
import com.theblastbeast.repository.ArticleRepository;
import com.theblastbeast.repository.BandRepository;
import com.theblastbeast.repository.CategoryRepository;
import com.theblastbeast.repository.MediaAssetRepository;
import com.theblastbeast.repository.TagRepository;
import com.theblastbeast.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de los tests de integración: arranca un PostgreSQL real con Testcontainers
 * una sola vez por JVM, limpia la base entre tests y ofrece utilidades de siembra
 * y autenticación.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String ADMIN_EMAIL = "redaccion@theblastbeast.com";
    protected static final String ADMIN_PASSWORD = "Redaccion2026!";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("blastbeast")
            .withUsername("blastbeast")
            .withPassword("blastbeast");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected CategoryRepository categoryRepository;

    @Autowired
    protected BandRepository bandRepository;

    @Autowired
    protected TagRepository tagRepository;

    @Autowired
    protected ArticleRepository articleRepository;

    @Autowired
    protected MediaAssetRepository mediaAssetRepository;

    @BeforeEach
    void prepareTest() {
        // Los tests negocian errores ProblemDetail sin depender del Accept del cliente.
        rest.getRestTemplate().setInterceptors(List.of((request, body, execution) -> {
            request.getHeaders().setAccept(List.of(MediaType.ALL));
            return execution.execute(request, body);
        }));
        jdbcTemplate.execute("""
                truncate table refresh_tokens, article_revisions, article_tags, article_bands,
                    media_assets, articles, tags, bands, categories, users
                restart identity cascade
                """);
    }

    protected User seedAdmin() {
        return seedUser(ADMIN_EMAIL, ADMIN_PASSWORD, Role.ADMIN);
    }

    protected User seedUser(String email, String rawPassword, Role role) {
        return seedUser(email, rawPassword, role, UserStatus.ACTIVE);
    }

    protected User seedUser(String email, String rawPassword, Role role, UserStatus status) {
        User user = new User();
        user.setEmail(email.toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setDisplayName("Redacción Blast Beast");
        user.setRole(role);
        user.setStatus(status);
        return userRepository.saveAndFlush(user);
    }

    protected Category seedCategory(String slug, String name) {
        Category category = new Category();
        category.setSlug(slug);
        category.setName(name);
        category.setDescription("Descripción de " + name);
        category.setSortOrder(0);
        return categoryRepository.saveAndFlush(category);
    }

    protected Band seedBand(String slug, String name) {
        Band band = new Band();
        band.setSlug(slug);
        band.setName(name);
        band.setCountry("España");
        band.setGenre("Heavy Metal");
        return bandRepository.saveAndFlush(band);
    }

    protected Tag seedTag(String slug, String name) {
        Tag tag = new Tag();
        tag.setSlug(slug);
        tag.setName(name);
        return tagRepository.saveAndFlush(tag);
    }

    protected Article seedArticle(String slug, String title, Category category, User author,
            ArticleStatus status, OffsetDateTime publishedAt) {
        Article article = new Article();
        article.setSlug(slug);
        article.setTitle(title);
        article.setExcerpt("Entradilla de " + title);
        article.setBodyMd("## " + title + "\n\nContenido de la noticia.");
        article.setBodyHtml("<h2>" + title + "</h2><p>Contenido de la noticia.</p>");
        article.setCategory(category);
        article.setAuthor(author);
        article.setStatus(status);
        article.setPublishedAt(publishedAt);
        article.setReadingMinutes(1);
        return articleRepository.saveAndFlush(article);
    }

    protected String loginAsAdmin() {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    protected String login(String email, String password) {
        ResponseEntity<AuthResponse> response = rest.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", email, "password", password),
                AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isNotBlank();
        return response.getBody().getAccessToken();
    }

    protected HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    protected HttpHeaders cookieHeaders(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return headers;
    }

    protected String refreshCookie(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).as("Set-Cookie de la respuesta").isNotNull();
        return cookies.stream()
                .filter(cookie -> cookie.startsWith("bb_refresh="))
                .findFirst()
                .map(cookie -> cookie.substring(0, cookie.indexOf(';')))
                .orElseThrow(() -> new AssertionError("No se encontró la cookie bb_refresh"));
    }
}
