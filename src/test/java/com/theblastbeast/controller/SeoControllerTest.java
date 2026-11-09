package com.theblastbeast.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.theblastbeast.properties.SiteProperties;
import com.theblastbeast.service.FeedService;
import com.theblastbeast.service.SitemapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SeoControllerTest {

    private static final SiteProperties PROPERTIES =
            new SiteProperties("The Blast Beast", "https://theblastbeast.test/", "es");

    private SitemapService sitemapService;
    private FeedService feedService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        sitemapService = org.mockito.Mockito.mock(SitemapService.class);
        feedService = org.mockito.Mockito.mock(FeedService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new SeoController(sitemapService, feedService, PROPERTIES)).build();
    }

    @Test
    @DisplayName("sitemap devuelve XML con ETag y responde 304 si coincide")
    void sitemapUsaEtag() throws Exception {
        when(sitemapService.build()).thenReturn("<?xml version=\"1.0\"?><urlset/>");

        MvcResult first = mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andReturn();

        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
        mockMvc.perform(get("/sitemap.xml").header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag));
    }

    @Test
    @DisplayName("robots.txt apunta al sitemap absoluto y bloquea el panel y la API")
    void robotsPublicaReglas() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("Disallow: /admin")))
                .andExpect(content().string(containsString("Disallow: /api/")))
                .andExpect(content().string(containsString(
                        "Sitemap: https://theblastbeast.test/sitemap.xml")));
    }
}
