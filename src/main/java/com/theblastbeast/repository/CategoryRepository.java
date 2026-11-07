package com.theblastbeast.repository;

import com.theblastbeast.domain.entity.ArticleStatus;
import com.theblastbeast.domain.entity.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("""
            select c.id as id,
                   c.slug as slug,
                   c.name as name,
                   c.description as description,
                   count(a.id) as articleCount
            from Category c
            left join Article a on a.category = c and a.status = :status
            group by c.id, c.slug, c.name, c.description, c.sortOrder
            order by c.sortOrder asc, c.name asc
            """)
    List<CategoryCountView> findAllWithArticleCount(@Param("status") ArticleStatus status);
}
