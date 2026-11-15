package com.theblastbeast.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Géneros de la agenda")
class ConcertGenreTest {

    @Test
    @DisplayName("prioriza el subgénero y normaliza los nombres de Ticketmaster")
    void normalizaGeneros() {
        assertThat(ConcertGenre.from("Metal", "Heavy Metal")).isEqualTo(ConcertGenre.METAL);
        assertThat(ConcertGenre.from("Rock", "Hard Rock")).isEqualTo(ConcertGenre.HARD_ROCK);
        assertThat(ConcertGenre.from("Rock", null)).isEqualTo(ConcertGenre.ROCK);
        assertThat(ConcertGenre.from("Punk", "Hardcore")).isEqualTo(ConcertGenre.PUNK);
        assertThat(ConcertGenre.from("Alternative", "Indie Rock"))
                .isEqualTo(ConcertGenre.INDIE);
        assertThat(ConcertGenre.from("Dance/Electronic", null))
                .isEqualTo(ConcertGenre.ELECTRONIC);
        assertThat(ConcertGenre.from("Hip-Hop/Rap", null)).isEqualTo(ConcertGenre.HIP_HOP);
        assertThat(ConcertGenre.from("Pop", null)).isEqualTo(ConcertGenre.POP);
        assertThat(ConcertGenre.from("Jazz", null)).isEqualTo(ConcertGenre.JAZZ);
        assertThat(ConcertGenre.from("Classical", null)).isEqualTo(ConcertGenre.CLASSICAL);
        assertThat(ConcertGenre.from("Folk", null)).isEqualTo(ConcertGenre.FOLK);
        assertThat(ConcertGenre.from("Latin", null)).isEqualTo(ConcertGenre.LATIN);
        assertThat(ConcertGenre.from("World", null)).isEqualTo(ConcertGenre.FOLK);
        assertThat(ConcertGenre.from(null, null)).isEqualTo(ConcertGenre.OTHER);
    }

    @Test
    @DisplayName("devuelve etiquetas legibles para los slugs")
    void etiquetasLegibles() {
        assertThat(ConcertGenre.labelForSlug("metal")).isEqualTo("Metal");
        assertThat(ConcertGenre.labelForSlug("hard-rock")).isEqualTo("Hard Rock");
        assertThat(ConcertGenre.labelForSlug("desconocido")).isEqualTo("Otras");
        assertThat(ConcertGenre.labelForSlug(null)).isEqualTo("Otras");
    }
}
