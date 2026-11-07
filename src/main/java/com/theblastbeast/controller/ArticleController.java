package com.theblastbeast.controller;

import com.theblastbeast.api.ArticlesApiDelegate;
import com.theblastbeast.domain.vo.ArticleDetail;
import com.theblastbeast.domain.vo.ArticlePage;
import com.theblastbeast.domain.vo.ArticleSort;
import com.theblastbeast.domain.vo.ArticleSummary;
import com.theblastbeast.repository.ArticleFilter;
import com.theblastbeast.service.ArticleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleController implements ArticlesApiDelegate {

    private final ArticleService articleService;

    @Override
    public ResponseEntity<ArticlePage> listArticles(
            Integer page,
            Integer size,
            String category,
            String tag,
            String band,
            String q,
            ArticleSort sort) {
        log.debug("INIT - ArticleController -> listArticles()");
        ArticleFilter filter = new ArticleFilter(category, tag, band, q, sort);
        ResponseEntity<ArticlePage> response =
                ResponseEntity.ok(articleService.list(filter, page, size));
        log.debug("END - ArticleController -> listArticles()");
        return response;
    }

    @Override
    public ResponseEntity<List<ArticleSummary>> listFeaturedArticles() {
        log.debug("INIT - ArticleController -> listFeaturedArticles()");
        ResponseEntity<List<ArticleSummary>> response =
                ResponseEntity.ok(articleService.featured());
        log.debug("END - ArticleController -> listFeaturedArticles()");
        return response;
    }

    @Override
    public ResponseEntity<ArticleDetail> getArticle(String slug) {
        log.debug("INIT - ArticleController -> getArticle() - slug={}", slug);
        ResponseEntity<ArticleDetail> response =
                ResponseEntity.ok(articleService.detail(slug));
        log.debug("END - ArticleController -> getArticle() - slug={}", slug);
        return response;
    }
}
