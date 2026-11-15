package com.theblastbeast.integration.ticketmaster;

import com.theblastbeast.domain.entity.ConcertStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record NormalizedConcert(
        String externalId,
        String title,
        String description,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        boolean timeTba,
        ConcertStatus status,
        String genre,
        String genreSlug,
        String subgenre,
        String ticketUrl,
        String sourceUrl,
        BigDecimal priceMin,
        BigDecimal priceMax,
        String currency,
        String imageUrl,
        String imageAttribution,
        NormalizedVenue venue,
        List<NormalizedArtist> artists) {

    public NormalizedConcert {
        artists = artists == null ? List.of() : List.copyOf(artists);
    }
}
