package com.theblastbeast.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SlugService {

    private static final int MAX_LENGTH = 160;

    public String slugify(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return truncate(normalized, MAX_LENGTH);
    }

    public String unique(String candidate, Predicate<String> exists) {
        String base = candidate;
        String slug = base;
        int suffix = 2;
        while (exists.test(slug)) {
            String suffixText = "-" + suffix++;
            slug = truncate(base, MAX_LENGTH - suffixText.length()) + suffixText;
        }
        return slug;
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).replaceAll("-+$", "");
    }
}
