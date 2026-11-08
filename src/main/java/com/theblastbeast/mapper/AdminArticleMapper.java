package com.theblastbeast.mapper;

import com.theblastbeast.domain.entity.Article;
import com.theblastbeast.domain.entity.ArticleRevision;
import com.theblastbeast.domain.entity.Band;
import com.theblastbeast.domain.entity.Category;
import com.theblastbeast.domain.entity.Tag;
import com.theblastbeast.domain.vo.AdminArticleDetail;
import com.theblastbeast.domain.vo.AdminArticleSummary;
import com.theblastbeast.domain.vo.ArticleRevisionResponse;
import com.theblastbeast.domain.vo.BandSummary;
import com.theblastbeast.domain.vo.CategorySummary;
import com.theblastbeast.domain.vo.TagSummary;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AdminArticleMapper {

    @Mapping(target = "authorName", source = "author.displayName")
    @Mapping(target = "cover", ignore = true)
    AdminArticleSummary toSummary(Article article);

    @Mapping(target = "authorName", source = "author.displayName")
    @Mapping(target = "cover", ignore = true)
    AdminArticleDetail toDetail(Article article);

    List<AdminArticleSummary> toSummaries(List<Article> articles);

    @Mapping(target = "editorName", source = "editor.displayName")
    ArticleRevisionResponse toRevision(ArticleRevision revision);

    CategorySummary toCategorySummary(Category category);

    BandSummary toBandSummary(Band band);

    TagSummary toTagSummary(Tag tag);
}
