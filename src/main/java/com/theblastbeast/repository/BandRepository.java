package com.theblastbeast.repository;

import com.theblastbeast.domain.entity.Band;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BandRepository extends JpaRepository<Band, Long> {

    Optional<Band> findBySlug(String slug);
}
