package com.theblastbeast.service.social;

import com.theblastbeast.domain.vo.AdminArticleDetail;
import com.theblastbeast.domain.vo.BandSummary;
import com.theblastbeast.domain.vo.SocialSummaryResponse;
import com.theblastbeast.domain.vo.SocialSummarySource;
import com.theblastbeast.domain.vo.SocialSummaryVariants;
import com.theblastbeast.domain.vo.TagSummary;
import com.theblastbeast.properties.SiteProperties;
import com.theblastbeast.service.AdminArticleService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Genera textos de difusión bajo demanda. La IA es opcional: si no está
 * configurada o falla, se devuelve una plantilla determinista para que el
 * editor siempre pueda copiar algo útil. Nunca publica automáticamente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialSummaryService {

    private static final int X_LIMIT = 280;
    private static final int FACEBOOK_LIMIT = 600;
    private static final int INSTAGRAM_LIMIT = 800;
    private static final int WHATSAPP_LIMIT = 400;
    private static final int CACHE_LIMIT = 200;

    private final AdminArticleService adminArticleService;
    private final SocialSummaryClient socialSummaryClient;
    private final SiteProperties siteProperties;
    private final Map<String, SocialSummaryResponse> cache = new ConcurrentHashMap<>();

    public SocialSummaryResponse generate(Long id) {
        AdminArticleDetail article = adminArticleService.get(id);
        String url = articleUrl(article);
        String cacheKey = article.getId() + "@" + article.getUpdatedAt() + "@" + url;

        SocialSummaryResponse cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        SocialSummaryDraft draft = socialSummaryClient.generate(context(article, url)).orElse(null);
        SocialSummaryResponse response = draft == null
                ? template(article, url)
                : fromAi(article, draft, url);

        // Solo se cachean los resultados de IA: una caída transitoria del
        // proveedor no debe congelar la plantilla hasta que cambie el artículo.
        if (draft != null) {
            if (cache.size() >= CACHE_LIMIT) {
                cache.clear();
            }
            cache.put(cacheKey, response);
        }
        log.debug("Resumen social generado para el artículo {} con origen {}",
                id, response.getSource());
        return response;
    }

    private SocialSummaryResponse fromAi(AdminArticleDetail article, SocialSummaryDraft draft,
            String url) {
        String x = withUrl(draft.x(), url);
        String facebook = withUrl(draft.facebook(), url);
        String instagram = withUrl(draft.instagram(), url);
        String whatsapp = withUrl(draft.whatsapp(), url);

        if (!within(x, X_LIMIT) || !within(facebook, FACEBOOK_LIMIT)
                || !within(instagram, INSTAGRAM_LIMIT) || !within(whatsapp, WHATSAPP_LIMIT)) {
            log.debug("La respuesta de IA no respeta los límites; se usa la plantilla");
            return template(article, url);
        }

        SocialSummaryVariants variants = new SocialSummaryVariants();
        variants.setX(x);
        variants.setFacebook(facebook);
        variants.setInstagram(instagram);
        variants.setWhatsapp(whatsapp);
        return response(SocialSummarySource.AI, variants);
    }

    private SocialSummaryResponse template(AdminArticleDetail article, String url) {
        SocialSummaryVariants variants = new SocialSummaryVariants();
        variants.setX(templateX(article, url));
        variants.setFacebook(templateFacebook(article, url));
        variants.setInstagram(templateInstagram(article, url));
        variants.setWhatsapp(templateWhatsapp(article, url));
        return response(SocialSummarySource.TEMPLATE, variants);
    }

    private static String withUrl(String text, String url) {
        String value = text == null ? "" : text.strip();
        if (value.isBlank()) {
            return null;
        }
        return value.contains(url) ? value : value + "\n\n" + url;
    }

    private static boolean within(String value, int max) {
        return value != null && value.length() <= max;
    }

    private static String templateX(AdminArticleDetail article, String url) {
        String suffix = "\n\n" + url;
        int room = X_LIMIT - suffix.length();
        if (room <= 0) {
            return url;
        }
        return truncate(article.getTitle(), room) + suffix;
    }

    private static String templateFacebook(AdminArticleDetail article, String url) {
        return compose(article.getTitle(), article.getExcerpt(), url, "", FACEBOOK_LIMIT);
    }

    private static String templateInstagram(AdminArticleDetail article, String url) {
        return compose(article.getTitle(), article.getExcerpt(), url, hashtags(article),
                INSTAGRAM_LIMIT);
    }

    private static String templateWhatsapp(AdminArticleDetail article, String url) {
        return compose(article.getTitle(), article.getExcerpt(), url, "", WHATSAPP_LIMIT);
    }

    private static String compose(String title, String excerpt, String url, String hashtags,
            int max) {
        String tail = hashtags == null || hashtags.isBlank()
                ? "\n\n" + url
                : "\n\n" + url + "\n\n" + hashtags;
        int room = max - tail.length();
        if (room <= 0) {
            return url;
        }

        String body = ((title == null ? "" : title.strip()) + "\n\n"
                + (excerpt == null ? "" : excerpt.strip())).strip();
        if (body.isBlank()) {
            return hashtags == null || hashtags.isBlank() ? url : url + "\n\n" + hashtags;
        }
        return truncate(body, room) + tail;
    }

    private static String truncate(String value, int max) {
        String text = value == null ? "" : value.strip();
        if (text.length() <= max) {
            return text;
        }
        int cut = text.lastIndexOf(' ', max - 1);
        if (cut < max / 2) {
            cut = max - 1;
        }
        return text.substring(0, Math.max(cut, 0)).stripTrailing() + "…";
    }

    private static String hashtags(AdminArticleDetail article) {
        Stream<String> tagNames = article.getTags() == null
                ? Stream.empty()
                : article.getTags().stream().map(TagSummary::getName);
        Stream<String> bandNames = article.getBands() == null
                ? Stream.empty()
                : article.getBands().stream().map(BandSummary::getName);

        return Stream.concat(tagNames, bandNames)
                .map(SocialSummaryService::hashtag)
                .filter(Objects::nonNull)
                .distinct()
                .limit(4)
                .collect(Collectors.joining(" "));
    }

    private static String hashtag(String name) {
        if (name == null) {
            return null;
        }
        String cleaned = name.replaceAll("[^\\p{L}\\p{N}]", "");
        return cleaned.isBlank() ? null : "#" + cleaned;
    }

    private static SocialSummaryContext context(AdminArticleDetail article, String url) {
        List<String> bands = article.getBands() == null
                ? List.of()
                : article.getBands().stream().map(BandSummary::getName).toList();
        List<String> tags = article.getTags() == null
                ? List.of()
                : article.getTags().stream().map(TagSummary::getName).toList();

        return new SocialSummaryContext(
                article.getTitle(),
                article.getExcerpt(),
                article.getBodyMd(),
                article.getCategory() == null ? "" : article.getCategory().getName(),
                bands,
                tags,
                url);
    }

    private String articleUrl(AdminArticleDetail article) {
        if (article.getCanonicalUrl() != null && !article.getCanonicalUrl().isBlank()) {
            return article.getCanonicalUrl().strip();
        }
        String base = siteProperties.baseUrl() == null
                ? ""
                : siteProperties.baseUrl().replaceAll("/+$", "");
        return base + "/articulos/" + article.getSlug();
    }

    private static SocialSummaryResponse response(SocialSummarySource source,
            SocialSummaryVariants variants) {
        SocialSummaryResponse response = new SocialSummaryResponse();
        response.setSource(source);
        response.setVariants(variants);
        return response;
    }
}
