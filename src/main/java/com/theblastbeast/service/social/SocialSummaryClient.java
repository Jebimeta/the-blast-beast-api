package com.theblastbeast.service.social;

import java.util.Optional;

/** Genera las variantes con IA. Devuelve vacío si no hay proveedor o falla. */
public interface SocialSummaryClient {

    Optional<SocialSummaryDraft> generate(SocialSummaryContext context);
}
