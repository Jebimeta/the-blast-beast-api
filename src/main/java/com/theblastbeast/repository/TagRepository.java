package com.theblastbeast.repository;

import com.theblastbeast.domain.entity.Tag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    @Query("""
            select t from Tag t
            where lower(t.name) like lower(concat('%', :q, '%'))
               or lower(t.slug) like lower(concat('%', :q, '%'))
            order by t.name asc
            """)
    List<Tag> search(@Param("q") String q);
}
