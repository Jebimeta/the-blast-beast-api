package com.theblastbeast.service.social;

/** Variantes devueltas por el proveedor de IA antes de validarlas. */
public record SocialSummaryDraft(String x, String facebook, String instagram, String whatsapp) {

    public boolean isComplete() {
        return hasText(x) && hasText(facebook) && hasText(instagram) && hasText(whatsapp);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
