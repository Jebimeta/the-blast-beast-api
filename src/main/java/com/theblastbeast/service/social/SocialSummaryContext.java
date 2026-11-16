package com.theblastbeast.service.social;

import java.util.List;

/** Datos mínimos del artículo que se envían al proveedor de IA. */
public record SocialSummaryContext(
        String title,
        String excerpt,
        String bodyMd,
        String category,
        List<String> bands,
        List<String> tags,
        String url) {}
