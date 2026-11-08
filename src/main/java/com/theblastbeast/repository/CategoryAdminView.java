package com.theblastbeast.repository;

public interface CategoryAdminView {

    Long getId();

    String getSlug();

    String getName();

    String getDescription();

    Integer getSortOrder();

    Long getArticleCount();
}
