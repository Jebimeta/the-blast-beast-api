package com.theblastbeast.repository;

import com.theblastbeast.domain.entity.Concert;
import com.theblastbeast.domain.entity.ConcertStatus;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConcertRepository extends JpaRepository<Concert, Long>,
        JpaSpecificationExecutor<Concert> {

    Optional<Concert> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @EntityGraph(attributePaths = {"venue", "artists", "artists.band"})
    Optional<Concert> findBySourceAndExternalId(String source, String externalId);

    boolean existsBySourceAndExternalId(String source, String externalId);

    @EntityGraph(attributePaths = {"artists", "artists.band"})
    List<Concert> findBySourceAndExternalIdIn(String source, Collection<String> externalIds);

    @Override
    @EntityGraph(attributePaths = {"venue"})
    Page<Concert> findAll(Specification<Concert> specification, Pageable pageable);

    @Query("""
            select distinct c
            from Concert c
            join fetch c.venue
            left join fetch c.artists a
            left join fetch a.band
            where c.slug = :slug
            """)
    Optional<Concert> findBySlugWithDetails(@Param("slug") String slug);

    @Query("""
            select c.genreSlug, c.genre, count(c)
            from Concert c
            where c.hidden = false
              and c.status not in :excluded
              and c.startAt >= :from
            group by c.genreSlug, c.genre
            order by count(c) desc, c.genre asc
            """)
    List<Object[]> countByGenre(
            @Param("excluded") List<ConcertStatus> excluded,
            @Param("from") OffsetDateTime from);

    @Query("""
            select v.citySlug, v.city, count(c)
            from Concert c
            join c.venue v
            where c.hidden = false
              and c.status not in :excluded
              and c.startAt >= :from
            group by v.citySlug, v.city
            order by count(c) desc, v.city asc
            """)
    List<Object[]> countByCity(
            @Param("excluded") List<ConcertStatus> excluded,
            @Param("from") OffsetDateTime from);

    @Query("""
            select a.slug, a.name, count(c)
            from Concert c
            join c.artists a
            where c.hidden = false
              and c.status not in :excluded
              and c.startAt >= :from
            group by a.slug, a.name
            order by count(c) desc, a.name asc
            """)
    List<Object[]> countByArtist(
            @Param("excluded") List<ConcertStatus> excluded,
            @Param("from") OffsetDateTime from,
            Pageable pageable);

    @Query(value = """
            select (c.start_at at time zone 'Europe/Madrid')::date as day, count(*) as total
            from concerts c
            where c.hidden = false
              and c.status not in ('CANCELLED', 'ARCHIVED')
              and c.start_at >= :from
              and c.start_at < :to
            group by day
            order by day
            """, nativeQuery = true)
    List<ConcertDayCountView> countByDay(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    @Query("""
            select min(c.startAt) as earliest, max(c.startAt) as latest
            from Concert c
            where c.hidden = false
              and c.status not in :excluded
              and c.startAt >= :from
            """)
    ConcertDateRangeView findDateRange(
            @Param("excluded") List<ConcertStatus> excluded,
            @Param("from") OffsetDateTime from);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Concert c
            set c.status = :archived
            where c.source = :source
              and c.lastSeenAt < :cutoff
              and c.startAt > :now
              and c.startAt < :until
              and c.status <> :archived
            """)
    int archiveNotSeen(
            @Param("source") String source,
            @Param("cutoff") OffsetDateTime cutoff,
            @Param("now") OffsetDateTime now,
            @Param("until") OffsetDateTime until,
            @Param("archived") ConcertStatus archived);

    @EntityGraph(attributePaths = {"venue"})
    @Query("""
            select c
            from Concert c
            where c.hidden = false
              and c.status not in :excluded
              and c.startAt >= :from
            order by c.startAt asc
            """)
    List<Concert> findUpcomingForSitemap(
            @Param("excluded") List<ConcertStatus> excluded,
            @Param("from") OffsetDateTime from,
            Pageable pageable);
}
