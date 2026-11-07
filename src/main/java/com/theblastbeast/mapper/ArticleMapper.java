package com.theblastbeast.mapper;

import com.theblastbeast.domain.entity.Article;
import com.theblastbeast.domain.entity.Band;
import com.theblastbeast.domain.entity.Category;
import com.theblastbeast.domain.entity.Tag;
import com.theblastbeast.domain.vo.ArticleDetail;
import com.theblastbeast.domain.vo.ArticleSummary;
import com.theblastbeast.domain.vo.BandSummary;
import com.theblastbeast.domain.vo.CategorySummary;
import com.theblastbeast.domain.vo.TagSummary;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ArticleMapper {

    @Mapping(target = "authorName", source = "author.displayName")
    ArticleSummary toSummary(Article article);

    @Mapping(target = "authorName", source = "author.displayName")
    ArticleDetail toDetail(Article article);

    CategorySummary toCategorySummary(Category category);

    BandSummary toBandSummary(Band band);

    TagSummary toTagSummary(Tag tag);

    List<ArticleSummary> toSummaries(List<Article> articles);
}
