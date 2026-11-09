package com.theblastbeast.service;

import com.theblastbeast.domain.entity.Article;
import com.theblastbeast.domain.entity.ArticleStatus;
import com.theblastbeast.domain.entity.Band;
import com.theblastbeast.domain.entity.Category;
import com.theblastbeast.domain.entity.MediaAsset;
import com.theblastbeast.domain.vo.MediaAssetKind;
import com.theblastbeast.domain.vo.MediaSummary;
import com.theblastbeast.domain.vo.MediaVariant;
import com.theblastbeast.properties.SiteProperties;
import com.theblastbeast.repository.ArticleRepository;
import com.theblastbeast.repository.BandRepository;
import com.theblastbeast.repository.CategoryRepository;
import com.theblastbeast.util.XmlSupport;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Genera {@code /sitemap.xml} con las URLs públicas indexables del portal.
 *
 * <p>Solo se incluyen artículos publicados. Los borradores y archivados quedan
 * fuera porque sus URLs responden 404 o 410.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SitemapService {

    private static final String SITEMAP_NS = "http://www.sitemaps.org/schemas/sitemap/0.9";
    private static final String IMAGE_NS = "http://www.google.com/schemas/sitemap-image/1.1";

    private final ArticleRepository articleRepository;
    private final CategoryRepository categoryRepository;
    private final BandRepository bandRepository;
    private final MediaService mediaService;
    private final SiteProperties siteProperties;

    @Transactional(readOnly = true)
    public String build() {
        log.debug("INIT - SitemapService -> build()");
        String base = baseUrl();

        List<Article> articles = articleRepository.findAllByStatusWithCover(ArticleStatus.PUBLISHED);
        List<Category> categories = categoryRepository.findAll(
                Sort.by("sortOrder").ascending().and(Sort.by("name").ascending()));
        List<Band> bands = bandRepository.findAll(Sort.by("name").ascending());

        List<MediaAsset> covers = articles.stream()
                .map(Article::getCoverMedia)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, MediaSummary> coversById = mediaService.toSummaryMap(covers);

        String latest = articles.stream()
                .map(Article::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(OffsetDateTime::compareTo)
                .map(XmlSupport::lastmod)
                .orElse(null);
        Map<Long, OffsetDateTime> categoryUpdates = categoryUpdates(articles);

        StringBuilder xml = new StringBuilder(4096);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"").append(SITEMAP_NS)
                .append("\" xmlns:image=\"").append(IMAGE_NS).append("\">\n");

        appendUrl(xml, base + "/", latest, "hourly", "1.0", null);
        appendUrl(xml, base + "/articulos", latest, "daily", "0.8", null);

        for (Category category : categories) {
            appendUrl(xml, base + "/categorias/" + category.getSlug(),
                    XmlSupport.lastmod(categoryUpdates.get(category.getId())), "daily", "0.6", null);
        }
        for (Band band : bands) {
            appendUrl(xml, base + "/bandas/" + band.getSlug(), null, "weekly", "0.5", null);
        }
        for (Article article : articles) {
            MediaAsset cover = article.getCoverMedia();
            appendUrl(xml, base + "/articulos/" + article.getSlug(),
                    XmlSupport.lastmod(article.getUpdatedAt()), "weekly", "0.7",
                    cover == null ? null : heroUrl(coversById.get(cover.getId())));
        }

        xml.append("</urlset>\n");
        log.debug("END - SitemapService -> build() - {} artículos", articles.size());
        return xml.toString();
    }

    private Map<Long, OffsetDateTime> categoryUpdates(List<Article> articles) {
        Map<Long, OffsetDateTime> updates = new HashMap<>();
        for (Article article : articles) {
            if (article.getUpdatedAt() == null || article.getCategory() == null) {
                continue;
            }
            Long categoryId = article.getCategory().getId();
            updates.merge(categoryId, article.getUpdatedAt(),
                    (current, candidate) -> candidate.isAfter(current) ? candidate : current);
        }
        return updates;
    }

    private void appendUrl(StringBuilder xml, String loc, String lastmod,
            String changefreq, String priority, String image) {
        xml.append("  <url>\n");
        xml.append("    <loc>").append(XmlSupport.escape(loc)).append("</loc>\n");
        if (lastmod != null) {
            xml.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        }
        xml.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        xml.append("    <priority>").append(priority).append("</priority>\n");
        if (image != null) {
            xml.append("    <image:image>\n");
            xml.append("      <image:loc>").append(XmlSupport.escape(image)).append("</image:loc>\n");
            xml.append("    </image:image>\n");
        }
        xml.append("  </url>\n");
    }

    private String heroUrl(MediaSummary summary) {
        if (summary == null) {
            return null;
        }
        return summary.getVariants().stream()
                .filter(variant -> variant.getKind() == MediaAssetKind.HERO)
                .map(MediaVariant::getUrl)
                .findFirst()
                .orElse(summary.getUrl());
    }

    private String baseUrl() {
        String base = siteProperties.baseUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
