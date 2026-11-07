package com.theblastbeast.repository;

import com.theblastbeast.domain.vo.ArticleSort;

public record ArticleFilter(
        String category,
        String tag,
        String band,
        String q,
        ArticleSort sort) {

    public ArticleFilter {
        if (sort == null) {
            sort = ArticleSort.LATEST;
        }
    }
}
