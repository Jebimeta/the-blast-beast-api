package com.theblastbeast.repository;

import com.theblastbeast.domain.entity.Release;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReleaseRepository extends JpaRepository<Release, Long>,
        JpaSpecificationExecutor<Release> {

    List<Release> findByHiddenFalseAndReleaseDateBetweenOrderByReleaseDateAscTitleAsc(
            LocalDate from, LocalDate to);

    Optional<Release> findTopByHiddenFalseOrderByUpdatedAtDesc();

    boolean existsBySlugAndIdNot(String slug, Long id);

    @Query("""
            select r from Release r
            where :q = ''
               or lower(r.title) like lower(concat('%', :q, '%'))
               or lower(r.artistName) like lower(concat('%', :q, '%'))
               or lower(coalesce(r.label, '')) like lower(concat('%', :q, '%'))
            order by r.releaseDate desc, r.title asc
            """)
    List<Release> searchAdmin(@Param("q") String q);
}
