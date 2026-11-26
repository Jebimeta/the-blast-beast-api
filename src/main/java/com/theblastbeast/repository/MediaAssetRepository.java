package com.theblastbeast.repository;

import com.theblastbeast.domain.entity.MediaAsset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    List<MediaAsset> findByParentIdOrderByKindAsc(Long parentId);

    List<MediaAsset> findByParentIdInOrderByKindAsc(Collection<Long> parentIds);

    Optional<MediaAsset> findByStorageKey(String storageKey);
}
