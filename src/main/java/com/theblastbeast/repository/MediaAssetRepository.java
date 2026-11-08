package com.theblastbeast.repository;

import com.theblastbeast.domain.entity.MediaAsset;
import com.theblastbeast.domain.entity.MediaKind;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    Page<MediaAsset> findByKind(MediaKind kind, Pageable pageable);

    List<MediaAsset> findByParentIdOrderByKindAsc(Long parentId);

    List<MediaAsset> findByParentIdInOrderByKindAsc(Collection<Long> parentIds);

    @Query("""
            select m from MediaAsset m
            where m.kind = com.theblastbeast.domain.entity.MediaKind.ORIGINAL
              and (lower(coalesce(m.altText, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(m.credit, '')) like lower(concat('%', :q, '%')))
            order by m.createdAt desc, m.id desc
            """)
    Page<MediaAsset> searchOriginals(@Param("q") String q, Pageable pageable);

    Optional<MediaAsset> findByStorageKey(String storageKey);
}
