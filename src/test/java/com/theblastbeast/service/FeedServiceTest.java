package com.theblastbeast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.theblastbeast.domain.entity.Article;
import com.theblastbeast.domain.entity.ArticleStatus;
import com.theblastbeast.domain.entity.Category;
import com.theblastbeast.domain.entity.MediaAsset;
import com.theblastbeast.domain.entity.User;
import com.theblastbeast.domain.vo.MediaAssetKind;
import com.theblastbeast.domain.vo.MediaSummary;
import com.theblastbeast.domain.vo.MediaVariant;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import com.theblastbeast.properties.SiteProperties;
import com.theblastbeast.repository.ArticleRepository;
import com.theblastbeast.repository.CategoryRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    private static final SiteProperties PROPERTIES =
            new SiteProperties("The Blast Beast", "https://theblastbeast.test", "es");

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MediaService mediaService;

    private FeedService feedService;
    private Article article;
    private Category category;

    @BeforeEach
    void setUp() {
        feedService = new FeedService(
                articleRepository, categoryRepository, mediaService, PROPERTIES);

        category = new Category();
        category.setId(1L);
        category.setSlug("noticias");
        category.setName("Noticias");
        category.setDescription("Actualidad de la escena.");

        User author = new User();
        author.setDisplayName("Redacción Blast Beast");

        article = new Article();
        article.setId(10L);
        article.setSlug("metallica-anuncia-gira");
        article.setTitle("Metallica & amigos anuncian gira");
        article.setExcerpt("Gira europea de estadios.");
        article.setBodyHtml("<p>Gira europea de estadios.</p>");
        article.setStatus(ArticleStatus.PUBLISHED);
        article.setCategory(category);
        article.setAuthor(author);
        article.setPublishedAt(OffsetDateTime.parse("2026-11-08T10:00:00Z"));
        article.setUpdatedAt(OffsetDateTime.parse("2026-11-08T11:00:00Z"));
    }

    @Test
    @DisplayName("genera RSS 2.0 con contenido completo, autor y portada hero")
    void generaFeedGlobal() {
        MediaAsset cover = new MediaAsset();
        cover.setId(5L);
        article.setCoverMedia(cover);

        MediaVariant hero = new MediaVariant();
        hero.setKind(MediaAssetKind.HERO);
        hero.setUrl("https://theblastbeast.test/media/portada-hero.webp");
        hero.setMimeType("image/webp");
        hero.setWidth(1600);
        hero.setHeight(1000);
        MediaSummary summary = new MediaSummary();
        summary.setUrl("https://theblastbeast.test/media/portada.png");
        summary.setVariants(List.of(hero));

        when(articleRepository.findPageByStatusWithCover(eq(ArticleStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(List.of(article));
        when(mediaService.toSummaryMap(any())).thenReturn(Map.of(5L, summary));

        String xml = feedService.globalFeed();

        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(xml).contains("<rss version=\"2.0\"");
        assertThat(xml).contains("<title>Metallica &amp; amigos anuncian gira</title>");
        assertThat(xml).contains("<content:encoded><![CDATA[<p>Gira europea de estadios.</p>]]></content:encoded>");
        assertThat(xml).contains("<dc:creator>Redacción Blast Beast</dc:creator>");
        assertThat(xml).contains("<category>Noticias</category>");
        assertThat(xml).contains("<guid isPermaLink=\"true\">"
                + "https://theblastbeast.test/articulos/metallica-anuncia-gira</guid>");
        assertThat(xml).contains("<media:content url=\"https://theblastbeast.test/media/portada-hero.webp\""
                + " medium=\"image\" type=\"image/webp\" width=\"1600\" height=\"1000\"/>");
        assertThat(xml).contains("<atom:link href=\"https://theblastbeast.test/feed.xml\""
                + " rel=\"self\" type=\"application/rss+xml\"/>");
    }

    @Test
    @DisplayName("el feed de categoría usa el nombre de la sección y su URL propia")
    void generaFeedDeCategoria() {
        when(categoryRepository.findBySlug("noticias")).thenReturn(Optional.of(category));
        when(articleRepository.findPageByCategorySlugAndStatus(
                eq("noticias"), eq(ArticleStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(List.of(article));
        when(mediaService.toSummaryMap(any())).thenReturn(Map.of());

        String xml = feedService.categoryFeed("noticias");

        assertThat(xml).contains("<title>The Blast Beast — Noticias</title>");
        assertThat(xml).contains("<link>https://theblastbeast.test/categorias/noticias</link>");
        assertThat(xml).contains("<atom:link href=\"https://theblastbeast.test/categorias/noticias/feed.xml\"");
    }

    @Test
    @DisplayName("una categoría desconocida responde con CATEGORY_NOT_FOUND")
    void categoriaDesconocidaLanzaError() {
        when(categoryRepository.findBySlug("inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> feedService.categoryFeed("inexistente"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }
}
