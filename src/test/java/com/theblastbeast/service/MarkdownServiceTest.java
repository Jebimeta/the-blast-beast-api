package com.theblastbeast.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarkdownServiceTest {

    private final MarkdownService markdownService = new MarkdownService();

    @Test
    @DisplayName("renderiza Markdown con tablas y enlaces")
    void renderizaMarkdown() {
        String html = markdownService.render("""
                ## Gira confirmada

                | Ciudad | Fecha |
                |--------|-------|
                | Madrid | 12/03 |

                [Entradas](https://example.com)
                """);

        assertThat(html).contains("<h2>Gira confirmada</h2>");
        assertThat(html).contains("<table>");
        assertThat(html).contains("<a href=\"https://example.com\" rel=\"nofollow\">Entradas</a>");
    }

    @Test
    @DisplayName("elimina HTML y atributos peligrosos")
    void saneaHtmlPeligroso() {
        String html = markdownService.render(
                "<script>alert('x')</script><img src=x onerror=alert(1)>Texto");

        assertThat(html).doesNotContain("<script>");
        assertThat(html).doesNotContain("onerror");
        assertThat(html).contains("Texto");
    }

    @Test
    @DisplayName("calcula minutos de lectura con un mínimo de uno")
    void calculaMinutosDeLectura() {
        assertThat(markdownService.readingMinutes("")).isZero();
        assertThat(markdownService.readingMinutes("Una noticia corta.")).isEqualTo(1);
        assertThat(markdownService.readingMinutes("palabra ".repeat(450))).isEqualTo(3);
    }
}
