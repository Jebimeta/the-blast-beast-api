package com.theblastbeast.repository;

import com.theblastbeast.domain.entity.Article;
import org.springframework.data.domain.Page;

public interface ArticleSearchRepository {

    Page<Article> search(ArticleFilter filter, int page, int size);
}
