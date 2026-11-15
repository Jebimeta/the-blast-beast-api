package com.theblastbeast.service;

import java.util.Locale;

/**
 * Géneros normalizados de la agenda.
 *
 * <p>Ticketmaster mezcla segmentos, géneros y subgéneros en inglés. La agenda
 * pública usa un conjunto pequeño y estable de slugs para que los filtros no
 * dependan de la fuente.
 */
public enum ConcertGenre {

    METAL("metal", "Metal"),
    HARD_ROCK("hard-rock", "Hard Rock"),
    ROCK("rock", "Rock"),
    PUNK("punk", "Punk"),
    ALTERNATIVE("alternative", "Alternativo"),
    INDIE("indie", "Indie"),
    POP("pop", "Pop"),
    HIP_HOP("hip-hop", "Hip-Hop"),
    ELECTRONIC("electronic", "Electrónica"),
    JAZZ("jazz", "Jazz"),
    CLASSICAL("classical", "Clásica"),
    FOLK("folk", "Folk"),
    LATIN("latin", "Latina"),
    OTHER("otras", "Otras");

    private final String slug;
    private final String label;

    ConcertGenre(String slug, String label) {
        this.slug = slug;
        this.label = label;
    }

    public String slug() {
        return slug;
    }

    public String label() {
        return label;
    }

    public static ConcertGenre from(String genre, String subgenre) {
        ConcertGenre bySubgenre = match(subgenre);
        if (bySubgenre != null) {
            return bySubgenre;
        }
        ConcertGenre byGenre = match(genre);
        return byGenre == null ? OTHER : byGenre;
    }

    public static String labelForSlug(String slug) {
        if (slug == null) {
            return OTHER.label;
        }
        for (ConcertGenre genre : values()) {
            if (genre.slug.equals(slug)) {
                return genre.label;
            }
        }
        return OTHER.label;
    }

    private static ConcertGenre match(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.contains("metal")) {
            return METAL;
        }
        if (normalized.contains("hard rock") || normalized.contains("hardrock")) {
            return HARD_ROCK;
        }
        if (normalized.contains("punk") || normalized.contains("hardcore")) {
            return PUNK;
        }
        if (normalized.contains("alternative") || normalized.contains("alternativo")) {
            return ALTERNATIVE;
        }
        if (normalized.contains("indie")) {
            return INDIE;
        }
        if (normalized.contains("rock")) {
            return ROCK;
        }
        if (normalized.contains("hip") || normalized.contains("rap")
                || normalized.contains("r&b") || normalized.contains("soul")) {
            return HIP_HOP;
        }
        if (normalized.contains("electronic") || normalized.contains("dance")
                || normalized.contains("techno") || normalized.contains("house")) {
            return ELECTRONIC;
        }
        if (normalized.contains("jazz") || normalized.contains("blues")) {
            return JAZZ;
        }
        if (normalized.contains("classic")) {
            return CLASSICAL;
        }
        if (normalized.contains("folk") || normalized.contains("country")
                || normalized.contains("world")) {
            return FOLK;
        }
        if (normalized.contains("latin") || normalized.contains("reggaeton")
                || normalized.contains("flamenco")) {
            return LATIN;
        }
        if (normalized.contains("pop")) {
            return POP;
        }
        return null;
    }
}
