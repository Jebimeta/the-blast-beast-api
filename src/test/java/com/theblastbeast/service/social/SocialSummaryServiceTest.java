package com.theblastbeast.service.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.theblastbeast.domain.vo.AdminArticleDetail;
import com.theblastbeast.domain.vo.BandSummary;
import com.theblastbeast.domain.vo.CategorySummary;
import com.theblastbeast.domain.vo.SocialSummaryResponse;
import com.theblastbeast.domain.vo.SocialSummarySource;
import com.theblastbeast.domain.vo.TagSummary;
import com.theblastbeast.properties.SiteProperties;
import com.theblastbeast.service.AdminArticleService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SocialSummaryServiceTest {

    private static final SiteProperties SITE =
            new SiteProperties("The Blast Beast", "https://theblastbeast.test", "es");
    private static final String URL =
            "https://theblastbeast.test/articulos/metallica-anuncia-gira";

    @Mock
    private AdminArticleService adminArticleService;

    @Mock
    private SocialSummaryClient socialSummaryClient;

    private SocialSummaryService service;
    private AdminArticleDetail article;

    @BeforeEach
    void setUp() {
        service = new SocialSummaryService(adminArticleService, socialSummaryClient, SITE);

        article = new AdminArticleDetail();
        article.setId(7L);
        article.setSlug("metallica-anuncia-gira");
        article.setTitle("Metallica anuncia una gira europea");
        article.setExcerpt("La banda confirma fechas en estadios para 2027.");
        article.setBodyMd("## Gira\n\nTexto de prueba sobre la gira.");
        article.setCategory(category());
        article.setBands(List.of(band()));
        article.setTags(List.of(tag()));
        article.setUpdatedAt(OffsetDateTime.parse("2026-11-16T10:00:00Z"));
        when(adminArticleService.get(7L)).thenReturn(article);
    }

    @Test
    @DisplayName("sin IA genera una plantilla con enlace y hashtags")
    void plantillaSinIa() {
        when(socialSummaryClient.generate(any())).thenReturn(Optional.empty());

        SocialSummaryResponse response = service.generate(7L);

        assertThat(response.getSource()).isEqualTo(SocialSummarySource.TEMPLATE);
        assertThat(response.getVariants().getX())
                .contains("Metallica anuncia una gira europea")
                .contains(URL)
                .hasSizeLessThanOrEqualTo(280);
        assertThat(response.getVariants().getFacebook())
                .contains("La banda confirma fechas en estadios")
                .contains(URL);
        assertThat(response.getVariants().getInstagram())
                .contains(URL)
                .contains("#Gira #Metallica");
        assertThat(response.getVariants().getWhatsapp()).contains(URL);
    }

    @Test
    @DisplayName("usa las variantes de IA y añade el enlace si falta")
    void variantesDeIa() {
        when(socialSummaryClient.generate(any())).thenReturn(Optional.of(new SocialSummaryDraft(
                "Gira europea de Metallica en 2027.",
                "Metallica ha confirmado las primeras fechas.",
                "Metallica vuelve a los estadios. #Metallica #Gira",
                "Metallica anuncia gira.")));

        SocialSummaryResponse response = service.generate(7L);

        assertThat(response.getSource()).isEqualTo(SocialSummarySource.AI);
        assertThat(response.getVariants().getX())
                .startsWith("Gira europea de Metallica")
                .contains(URL);
        assertThat(response.getVariants().getFacebook())
                .isEqualTo("Metallica ha confirmado las primeras fechas.\n\n" + URL);
        assertThat(response.getVariants().getInstagram())
                .isEqualTo("Metallica vuelve a los estadios. #Metallica #Gira\n\n" + URL);
        assertThat(response.getVariants().getWhatsapp()).contains(URL);
    }

    @Test
    @DisplayName("si la IA se pasa de límite cae a la plantilla completa")
    void iaFueraDeLimite() {
        when(socialSummaryClient.generate(any())).thenReturn(Optional.of(new SocialSummaryDraft(
                "x".repeat(300), "Facebook válido", "Instagram válido", "WhatsApp válido")));

        SocialSummaryResponse response = service.generate(7L);

        assertThat(response.getSource()).isEqualTo(SocialSummarySource.TEMPLATE);
        assertThat(response.getVariants().getX())
                .contains("Metallica anuncia una gira europea")
                .contains(URL)
                .hasSizeLessThanOrEqualTo(280);
    }

    @Test
    @DisplayName("respeta la URL canónica y envía el contexto completo a la IA")
    void usaCanonicaYContexto() {
        article.setCanonicalUrl("https://example.test/noticia-canonica");
        when(socialSummaryClient.generate(any())).thenReturn(Optional.empty());

        service.generate(7L);

        ArgumentCaptor<SocialSummaryContext> captor =
                ArgumentCaptor.forClass(SocialSummaryContext.class);
        verify(socialSummaryClient).generate(captor.capture());
        SocialSummaryContext context = captor.getValue();

        assertThat(context.url()).isEqualTo("https://example.test/noticia-canonica");
        assertThat(context.category()).isEqualTo("Noticias");
        assertThat(context.bands()).containsExactly("Metallica");
        assertThat(context.tags()).containsExactly("Gira");
        assertThat(context.bodyMd()).contains("Texto de prueba");
        assertThat(service.generate(7L).getVariants().getX())
                .contains("https://example.test/noticia-canonica");
    }

    @Test
    @DisplayName("reutiliza el resumen de IA mientras el artículo no cambie")
    void cacheaResultadoDeIa() {
        when(socialSummaryClient.generate(any())).thenReturn(Optional.of(new SocialSummaryDraft(
                "Texto X", "Texto Facebook", "Texto Instagram", "Texto WhatsApp")));

        service.generate(7L);
        service.generate(7L);

        verify(socialSummaryClient, times(1)).generate(any());
    }

    private static CategorySummary category() {
        CategorySummary category = new CategorySummary();
        category.setId(1L);
        category.setSlug("noticias");
        category.setName("Noticias");
        return category;
    }

    private static BandSummary band() {
        BandSummary band = new BandSummary();
        band.setId(2L);
        band.setSlug("metallica");
        band.setName("Metallica");
        return band;
    }

    private static TagSummary tag() {
        TagSummary tag = new TagSummary();
        tag.setId(3L);
        tag.setSlug("gira");
        tag.setName("Gira");
        return tag;
    }
}
