package com.theblastbeast.integration.ticketmaster;

import java.math.BigDecimal;

public record NormalizedVenue(
        String externalId,
        String name,
        String city,
        String citySlug,
        String region,
        String countryCode,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String timezone) {
}
