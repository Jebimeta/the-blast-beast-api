package com.theblastbeast.repository;

import com.theblastbeast.domain.entity.Article;
import com.theblastbeast.domain.entity.ArticleStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleRepository extends JpaRepository<Article, Long>, ArticleSearchRepository {

    Optional<Article> findBySlug(String slug);

    List<Article> findByStatusOrderByPublishedAtDesc(ArticleStatus status, Pageable pageable);

    @Query("""
            select distinct a
            from Article a
            join a.bands b
            where b.slug = :slug and a.status = :status
            order by a.publishedAt desc
            """)
    List<Article> findByBandSlug(
            @Param("slug") String slug,
            @Param("status") ArticleStatus status,
            Pageable pageable);
}
