package com.theblastbeast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.theblastbeast.domain.entity.Article;
import com.theblastbeast.domain.entity.ArticleStatus;
import com.theblastbeast.domain.entity.Band;
import com.theblastbeast.domain.entity.Category;
import com.theblastbeast.domain.entity.MediaAsset;
import com.theblastbeast.domain.entity.User;
import com.theblastbeast.domain.vo.MediaAssetKind;
import com.theblastbeast.domain.vo.MediaSummary;
import com.theblastbeast.domain.vo.MediaVariant;
import com.theblastbeast.properties.SiteProperties;
import com.theblastbeast.repository.ArticleRepository;
import com.theblastbeast.repository.BandRepository;
import com.theblastbeast.repository.CategoryRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class SitemapServiceTest {

    private static final SiteProperties PROPERTIES =
            new SiteProperties("The Blast Beast", "https://theblastbeast.test/", "es");

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BandRepository bandRepository;

    @Mock
    private MediaService mediaService;

    private SitemapService sitemapService;
    private Article article;
    private Category category;
    private Band band;

    @BeforeEach
    void setUp() {
        sitemapService = new SitemapService(
                articleRepository, categoryRepository, bandRepository, mediaService, PROPERTIES);

        category = new Category();
        category.setId(1L);
        category.setSlug("noticias");
        category.setName("Noticias");
        category.setSortOrder(1);

        band = new Band();
        band.setId(2L);
        band.setSlug("metallica");
        band.setName("Metallica");

        User author = new User();
        author.setDisplayName("Redacción Blast Beast");

        article = new Article();
        article.setId(10L);
        article.setSlug("metallica-anuncia-gira");
        article.setTitle("Metallica anuncia gira");
        article.setExcerpt("Gira europea de estadios.");
        article.setBodyHtml("<p>Gira europea de estadios.</p>");
        article.setStatus(ArticleStatus.PUBLISHED);
        article.setCategory(category);
        article.setAuthor(author);
        article.setPublishedAt(OffsetDateTime.parse("2026-11-08T10:00:00Z"));
        article.setUpdatedAt(OffsetDateTime.parse("2026-11-08T11:00:00Z"));
    }

    @Test
    @DisplayName("incluye portada, archivo, taxonomía y artículos publicados con URLs absolutas")
    void incluyeUrlsPublicas() {
        when(articleRepository.findAllByStatusWithCover(ArticleStatus.PUBLISHED))
                .thenReturn(List.of(article));
        when(categoryRepository.findAll(any(Sort.class))).thenReturn(List.of(category));
        when(bandRepository.findAll(any(Sort.class))).thenReturn(List.of(band));
        when(mediaService.toSummaryMap(any())).thenReturn(Map.of());

        String xml = sitemapService.build();

        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(xml).contains("<loc>https://theblastbeast.test/</loc>");
        assertThat(xml).contains("<loc>https://theblastbeast.test/articulos</loc>");
        assertThat(xml).contains("<loc>https://theblastbeast.test/categorias/noticias</loc>");
        assertThat(xml).contains("<loc>https://theblastbeast.test/bandas/metallica</loc>");
        assertThat(xml).contains("<loc>https://theblastbeast.test/articulos/metallica-anuncia-gira</loc>");
        assertThat(xml).contains("<lastmod>2026-11-08T11:00:00Z</lastmod>");
        verify(articleRepository).findAllByStatusWithCover(ArticleStatus.PUBLISHED);
    }

    @Test
    @DisplayName("usa la variante hero como imagen del artículo")
    void usaVarianteHeroComoImagen() {
        MediaAsset cover = new MediaAsset();
        cover.setId(5L);
        article.setCoverMedia(cover);

        MediaVariant hero = new MediaVariant();
        hero.setKind(MediaAssetKind.HERO);
        hero.setUrl("https://theblastbeast.test/media/portada-hero.webp");
        MediaSummary summary = new MediaSummary();
        summary.setUrl("https://theblastbeast.test/media/portada.png");
        summary.setVariants(List.of(hero));

        when(articleRepository.findAllByStatusWithCover(ArticleStatus.PUBLISHED))
                .thenReturn(List.of(article));
        when(categoryRepository.findAll(any(Sort.class))).thenReturn(List.of(category));
        when(bandRepository.findAll(any(Sort.class))).thenReturn(List.of(band));
        when(mediaService.toSummaryMap(any())).thenReturn(Map.of(5L, summary));

        String xml = sitemapService.build();

        assertThat(xml).contains(
                "<image:loc>https://theblastbeast.test/media/portada-hero.webp</image:loc>");
    }

    @Test
    @DisplayName("omite lastmod cuando todavía no hay artículos publicados")
    void omiteLastmodSinArticulos() {
        when(articleRepository.findAllByStatusWithCover(ArticleStatus.PUBLISHED))
                .thenReturn(List.of());
        when(categoryRepository.findAll(any(Sort.class))).thenReturn(List.of());
        when(bandRepository.findAll(any(Sort.class))).thenReturn(List.of());
        when(mediaService.toSummaryMap(any())).thenReturn(Map.of());

        String xml = sitemapService.build();

        assertThat(xml).doesNotContain("<lastmod>");
        assertThat(xml).contains("<loc>https://theblastbeast.test/</loc>");
    }
}
