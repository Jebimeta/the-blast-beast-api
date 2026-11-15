package com.theblastbeast.repository;

import com.theblastbeast.domain.entity.Venue;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    Optional<Venue> findBySourceAndExternalId(String source, String externalId);

    List<Venue> findBySourceAndExternalIdIn(String source, Collection<String> externalIds);
}
