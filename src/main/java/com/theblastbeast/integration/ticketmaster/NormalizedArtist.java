package com.theblastbeast.integration.ticketmaster;

public record NormalizedArtist(
        String externalId,
        String name,
        String slug,
        boolean headliner,
        int sortOrder) {
}
