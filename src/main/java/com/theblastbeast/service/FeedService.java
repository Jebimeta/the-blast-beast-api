package com.theblastbeast.service;

import com.theblastbeast.domain.entity.Article;
import com.theblastbeast.domain.entity.ArticleStatus;
import com.theblastbeast.domain.entity.Category;
import com.theblastbeast.domain.entity.MediaAsset;
import com.theblastbeast.domain.vo.MediaAssetKind;
import com.theblastbeast.domain.vo.MediaSummary;
import com.theblastbeast.domain.vo.MediaVariant;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import com.theblastbeast.properties.SiteProperties;
import com.theblastbeast.repository.ArticleRepository;
import com.theblastbeast.repository.CategoryRepository;
import com.theblastbeast.util.XmlSupport;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Genera el feed RSS 2.0 global y el de cada categoría.
 *
 * <p>Incluye el HTML completo y saneado del artículo en {@code content:encoded},
 * más la variante {@code hero} de la portada en Media RSS.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedService {

    private static final int FEED_SIZE = 30;
    private static final String DEFAULT_DESCRIPTION =
            "Noticias de heavy metal, lanzamientos, conciertos y entrevistas. Información de la escena sin relleno.";

    private final ArticleRepository articleRepository;
    private final CategoryRepository categoryRepository;
    private final MediaService mediaService;
    private final SiteProperties siteProperties;

    @Transactional(readOnly = true)
    public String globalFeed() {
        log.debug("INIT - FeedService -> globalFeed()");
        String base = baseUrl();
        List<Article> articles = articleRepository.findPageByStatusWithCover(
                ArticleStatus.PUBLISHED, PageRequest.of(0, FEED_SIZE));
        String feed = buildFeed(
                base + "/feed.xml",
                base + "/",
                siteProperties.name(),
                DEFAULT_DESCRIPTION,
                articles);
        log.debug("END - FeedService -> globalFeed() - {} artículos", articles.size());
        return feed;
    }

    @Transactional(readOnly = true)
    public String categoryFeed(String slug) {
        log.debug("INIT - FeedService -> categoryFeed() - slug={}", slug);
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        String base = baseUrl();
        List<Article> articles = articleRepository.findPageByCategorySlugAndStatus(
                slug, ArticleStatus.PUBLISHED, PageRequest.of(0, FEED_SIZE));
        String description = category.getDescription() == null || category.getDescription().isBlank()
                ? DEFAULT_DESCRIPTION
                : category.getDescription();
        String feed = buildFeed(
                base + "/categorias/" + category.getSlug() + "/feed.xml",
                base + "/categorias/" + category.getSlug(),
                siteProperties.name() + " — " + category.getName(),
                description,
                articles);
        log.debug("END - FeedService -> categoryFeed() - {} artículos", articles.size());
        return feed;
    }

    private String buildFeed(String selfUrl, String link, String title, String description,
            List<Article> articles) {
        List<MediaAsset> covers = articles.stream()
                .map(Article::getCoverMedia)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, MediaSummary> coversById = mediaService.toSummaryMap(covers);

        OffsetDateTime lastBuild = articles.stream()
                .map(Article::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(OffsetDateTime::compareTo)
                .orElseGet(() -> OffsetDateTime.now(ZoneOffset.UTC));

        StringBuilder xml = new StringBuilder(8192);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<rss version=\"2.0\"")
                .append(" xmlns:content=\"http://purl.org/rss/1.0/modules/content/\"")
                .append(" xmlns:atom=\"http://www.w3.org/2005/Atom\"")
                .append(" xmlns:media=\"http://search.yahoo.com/mrss/\"")
                .append(" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n");
        xml.append("  <channel>\n");
        xml.append("    <title>").append(XmlSupport.escape(title)).append("</title>\n");
        xml.append("    <link>").append(XmlSupport.escape(link)).append("</link>\n");
        xml.append("    <description>").append(XmlSupport.escape(description)).append("</description>\n");
        xml.append("    <language>es-ES</language>\n");
        xml.append("    <lastBuildDate>").append(XmlSupport.rfc1123(lastBuild)).append("</lastBuildDate>\n");
        xml.append("    <atom:link href=\"").append(XmlSupport.escape(selfUrl))
                .append("\" rel=\"self\" type=\"application/rss+xml\"/>\n");
        xml.append("    <generator>").append(XmlSupport.escape(siteProperties.name())).append("</generator>\n");

        for (Article article : articles) {
            appendItem(xml, baseUrl(), article, coversById);
        }

        xml.append("  </channel>\n");
        xml.append("</rss>\n");
        return xml.toString();
    }

    private void appendItem(StringBuilder xml, String base, Article article,
            Map<Long, MediaSummary> coversById) {
        String link = base + "/articulos/" + article.getSlug();
        xml.append("    <item>\n");
        xml.append("      <title>").append(XmlSupport.escape(article.getTitle())).append("</title>\n");
        xml.append("      <link>").append(XmlSupport.escape(link)).append("</link>\n");
        xml.append("      <guid isPermaLink=\"true\">").append(XmlSupport.escape(link)).append("</guid>\n");
        xml.append("      <pubDate>").append(XmlSupport.rfc1123(article.getPublishedAt())).append("</pubDate>\n");
        if (article.getAuthor() != null) {
            xml.append("      <dc:creator>")
                    .append(XmlSupport.escape(article.getAuthor().getDisplayName()))
                    .append("</dc:creator>\n");
        }
        if (article.getCategory() != null) {
            xml.append("      <category>")
                    .append(XmlSupport.escape(article.getCategory().getName()))
                    .append("</category>\n");
        }
        xml.append("      <description>").append(XmlSupport.escape(article.getExcerpt())).append("</description>\n");
        xml.append("      <content:encoded>").append(XmlSupport.cdata(article.getBodyHtml()))
                .append("</content:encoded>\n");

        MediaAsset cover = article.getCoverMedia();
        if (cover != null) {
            appendMedia(xml, coversById.get(cover.getId()));
        }
        xml.append("    </item>\n");
    }

    private void appendMedia(StringBuilder xml, MediaSummary summary) {
        if (summary == null) {
            return;
        }
        String url = summary.getUrl();
        String type = summary.getMimeType();
        Integer width = summary.getWidth();
        Integer height = summary.getHeight();

        for (MediaVariant variant : summary.getVariants()) {
            if (variant.getKind() == MediaAssetKind.HERO) {
                url = variant.getUrl();
                type = variant.getMimeType();
                width = variant.getWidth();
                height = variant.getHeight();
                break;
            }
        }

        xml.append("      <media:content url=\"").append(XmlSupport.escape(url))
                .append("\" medium=\"image\"");
        if (type != null) {
            xml.append(" type=\"").append(XmlSupport.escape(type)).append("\"");
        }
        if (width != null) {
            xml.append(" width=\"").append(width).append("\"");
        }
        if (height != null) {
            xml.append(" height=\"").append(height).append("\"");
        }
        xml.append("/>\n");
    }

    private String baseUrl() {
        String base = siteProperties.baseUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
