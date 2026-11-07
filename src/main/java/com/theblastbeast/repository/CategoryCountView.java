package com.theblastbeast.repository;

public interface CategoryCountView {

    Long getId();

    String getSlug();

    String getName();

    String getDescription();

    Long getArticleCount();
}
