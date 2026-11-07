package com.theblastbeast.controller;

import com.theblastbeast.api.SearchApiDelegate;
import com.theblastbeast.domain.vo.ArticlePage;
import com.theblastbeast.service.ArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchController implements SearchApiDelegate {

    private final ArticleService articleService;

    @Override
    public ResponseEntity<ArticlePage> searchArticles(String q, Integer page, Integer size) {
        log.debug("INIT - SearchController -> searchArticles() - q={}", q);
        ResponseEntity<ArticlePage> response =
                ResponseEntity.ok(articleService.search(q, page, size));
        log.debug("END - SearchController -> searchArticles() - q={}", q);
        return response;
    }
}
