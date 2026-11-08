package com.theblastbeast.service;

import com.theblastbeast.domain.entity.Article;
import com.theblastbeast.domain.entity.ArticleStatus;
import com.theblastbeast.domain.entity.MediaAsset;
import com.theblastbeast.domain.vo.ArticleDetail;
import com.theblastbeast.domain.vo.ArticlePage;
import com.theblastbeast.domain.vo.ArticleSort;
import com.theblastbeast.domain.vo.ArticleSummary;
import com.theblastbeast.domain.vo.MediaSummary;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import com.theblastbeast.mapper.ArticleMapper;
import com.theblastbeast.repository.ArticleFilter;
import com.theblastbeast.repository.ArticleRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleService {

    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int FEATURED_SIZE = 5;
    private static final int BAND_ARTICLES_SIZE = 20;

    private final ArticleRepository articleRepository;
    private final ArticleMapper articleMapper;
    private final MediaService mediaService;

    @Transactional(readOnly = true)
    public ArticlePage list(ArticleFilter filter, Integer page, Integer size) {
        log.debug("INIT - ArticleService -> list()");
        int safePage = page == null ? 0 : Math.max(page, 0);
        int safeSize = size == null
                ? DEFAULT_PAGE_SIZE
                : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<Article> result = articleRepository.search(filter, safePage, safeSize);
        ArticlePage response = toPage(result);
        log.debug("END - ArticleService -> list() - {} artículos", result.getNumberOfElements());
        return response;
    }

    @Transactional(readOnly = true)
    public List<ArticleSummary> featured() {
        log.debug("INIT - ArticleService -> featured()");
        List<ArticleSummary> response = toSummaries(
                articleRepository.findByStatusOrderByPublishedAtDesc(
                        ArticleStatus.PUBLISHED, PageRequest.of(0, FEATURED_SIZE)));
        log.debug("END - ArticleService -> featured() - {} artículos", response.size());
        return response;
    }

    @Transactional(readOnly = true)
    public ArticleDetail detail(String slug) {
        log.debug("INIT - ArticleService -> detail() - slug={}", slug);
        Article article = articleRepository.findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

        if (article.getStatus() == ArticleStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_PUBLISHED);
        }
        if (article.getStatus() != ArticleStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        }

        ArticleDetail response = articleMapper.toDetail(article);
        response.setCover(mediaService.toSummary(article.getCoverMedia()));
        log.debug("END - ArticleService -> detail() - slug={}", slug);
        return response;
    }

    @Transactional(readOnly = true)
    public ArticlePage search(String q, Integer page, Integer size) {
        log.debug("INIT - ArticleService -> search() - q={}", q);
        if (!StringUtils.hasText(q) || q.trim().length() < 2) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR, "La búsqueda necesita al menos 2 caracteres");
        }
        ArticlePage response = list(
                new ArticleFilter(null, null, null, q.trim(), ArticleSort.RELEVANCE), page, size);
        log.debug("END - ArticleService -> search() - {} resultados", response.getTotalElements());
        return response;
    }

    @Transactional(readOnly = true)
    public List<ArticleSummary> byBand(String slug) {
        return toSummaries(articleRepository.findByBandSlug(
                slug, ArticleStatus.PUBLISHED, PageRequest.of(0, BAND_ARTICLES_SIZE)));
    }

    private ArticlePage toPage(Page<Article> result) {
        ArticlePage response = new ArticlePage();
        response.setContent(toSummaries(result.getContent()));
        response.setPage(result.getNumber());
        response.setSize(result.getSize());
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        response.setFirst(result.isFirst());
        response.setLast(result.isLast());
        return response;
    }

    private List<ArticleSummary> toSummaries(List<Article> articles) {
        List<ArticleSummary> summaries = articleMapper.toSummaries(articles);
        List<MediaAsset> covers = articles.stream()
                .map(Article::getCoverMedia)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, MediaSummary> coversById = mediaService.toSummaryMap(covers);
        for (int index = 0; index < articles.size(); index++) {
            MediaAsset cover = articles.get(index).getCoverMedia();
            summaries.get(index).setCover(cover == null ? null : coversById.get(cover.getId()));
        }
        return summaries;
    }
}
