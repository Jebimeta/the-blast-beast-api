package com.theblastbeast.repository;

import com.theblastbeast.domain.entity.ArticleRevision;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleRevisionRepository extends JpaRepository<ArticleRevision, Long> {

    List<ArticleRevision> findByArticleIdOrderByCreatedAtDesc(Long articleId);

    Optional<ArticleRevision> findByIdAndArticleId(Long id, Long articleId);
}
