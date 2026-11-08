package com.theblastbeast.repository;

import com.theblastbeast.domain.entity.Band;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BandRepository extends JpaRepository<Band, Long> {

    Optional<Band> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    @Query("""
            select b from Band b
            where lower(b.name) like lower(concat('%', :q, '%'))
               or lower(b.slug) like lower(concat('%', :q, '%'))
            order by b.name asc
            """)
    List<Band> search(@Param("q") String q);
}
