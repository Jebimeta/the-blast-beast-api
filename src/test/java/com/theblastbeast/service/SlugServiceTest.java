package com.theblastbeast.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlugServiceTest {

    private final SlugService slugService = new SlugService();

    @Test
    @DisplayName("normaliza acentos, espacios y mayúsculas")
    void normalizaTexto() {
        assertThat(slugService.slugify("  Metallica anuncia gira por España  "))
                .isEqualTo("metallica-anuncia-gira-por-espana");
        assertThat(slugService.slugify("AC/DC + Motörhead")).isEqualTo("ac-dc-motorhead");
    }

    @Test
    @DisplayName("añade sufijo cuando el slug ya existe")
    void anadeSufijoCuandoExiste() {
        Set<String> taken = Set.of("opeth", "opeth-2");

        String slug = slugService.unique("opeth", taken::contains);

        assertThat(slug).isEqualTo("opeth-3");
    }
}
