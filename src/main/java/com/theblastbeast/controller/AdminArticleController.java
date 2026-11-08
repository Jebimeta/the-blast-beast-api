package com.theblastbeast.controller;

import com.theblastbeast.api.AdminArticlesApiDelegate;
import com.theblastbeast.domain.vo.AdminArticleDetail;
import com.theblastbeast.domain.vo.AdminArticlePage;
import com.theblastbeast.domain.vo.AdminArticleStatus;
import com.theblastbeast.domain.vo.ArticleRevisionResponse;
import com.theblastbeast.domain.vo.ArticleWriteRequest;
import com.theblastbeast.domain.vo.MarkdownPreviewRequest;
import com.theblastbeast.domain.vo.MarkdownPreviewResponse;
import com.theblastbeast.service.AdminArticleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminArticleController implements AdminArticlesApiDelegate {

    private final AdminArticleService adminArticleService;

    @Override
    public ResponseEntity<AdminArticlePage> listAdminArticles(Integer page, Integer size,
            AdminArticleStatus status, String q) {
        return ResponseEntity.ok(adminArticleService.list(page, size, status, q));
    }

    @Override
    public ResponseEntity<AdminArticleDetail> createAdminArticle(ArticleWriteRequest request) {
        log.debug("INIT - AdminArticleController -> createAdminArticle()");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminArticleService.create(request));
    }

    @Override
    public ResponseEntity<AdminArticleDetail> getAdminArticle(Long id) {
        return ResponseEntity.ok(adminArticleService.get(id));
    }

    @Override
    public ResponseEntity<AdminArticleDetail> updateAdminArticle(Long id, ArticleWriteRequest request) {
        return ResponseEntity.ok(adminArticleService.update(id, request));
    }

    @Override
    public ResponseEntity<Void> deleteAdminArticle(Long id) {
        adminArticleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<AdminArticleDetail> publishAdminArticle(Long id) {
        return ResponseEntity.ok(adminArticleService.publish(id));
    }

    @Override
    public ResponseEntity<AdminArticleDetail> unpublishAdminArticle(Long id) {
        return ResponseEntity.ok(adminArticleService.unpublish(id));
    }

    @Override
    public ResponseEntity<AdminArticleDetail> archiveAdminArticle(Long id) {
        return ResponseEntity.ok(adminArticleService.archive(id));
    }

    @Override
    public ResponseEntity<List<ArticleRevisionResponse>> listArticleRevisions(Long id) {
        return ResponseEntity.ok(adminArticleService.revisions(id));
    }

    @Override
    public ResponseEntity<AdminArticleDetail> restoreArticleRevision(Long id, Long revisionId) {
        return ResponseEntity.ok(adminArticleService.restore(id, revisionId));
    }

    @Override
    public ResponseEntity<MarkdownPreviewResponse> previewMarkdown(MarkdownPreviewRequest request) {
        return ResponseEntity.ok(adminArticleService.preview(request.getBodyMd()));
    }
}
