package com.theblastbeast.repository;

import com.theblastbeast.domain.entity.Article;
import com.theblastbeast.domain.vo.ArticleSort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * Búsqueda de artículos con SQL nativo porque PostgreSQL necesita operadores que JPQL no
 * expone: {@code @@} sobre {@code tsvector} y {@code ts_rank}.
 */
@Repository
public class ArticleSearchRepositoryImpl implements ArticleSearchRepository {

    private static final String FROM = " from articles a join categories c on c.id = a.category_id ";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Article> search(ArticleFilter filter, int page, int size) {
        Map<String, Object> parameters = new HashMap<>();
        String where = buildWhere(filter, parameters);

        long total = count(where, parameters);
        PageRequest pageRequest = PageRequest.of(page, size);
        if (total == 0) {
            return new PageImpl<>(List.of(), pageRequest, 0);
        }

        Query query = entityManager.createNativeQuery(
                "select a.* " + FROM + where + buildOrderBy(filter), Article.class);
        bind(query, parameters);
        query.setFirstResult(page * size);
        query.setMaxResults(size);

        @SuppressWarnings("unchecked")
        List<Article> content = query.getResultList();
        return new PageImpl<>(content, pageRequest, total);
    }

    private long count(String where, Map<String, Object> parameters) {
        Query query = entityManager.createNativeQuery("select count(*) " + FROM + where);
        bind(query, parameters);
        return ((Number) query.getSingleResult()).longValue();
    }

    private String buildWhere(ArticleFilter filter, Map<String, Object> parameters) {
        StringBuilder where = new StringBuilder(" where a.status = 'PUBLISHED'");

        if (StringUtils.hasText(filter.category())) {
            where.append(" and c.slug = :category");
            parameters.put("category", filter.category().trim());
        }
        if (StringUtils.hasText(filter.tag())) {
            where.append("""
                     and exists (
                        select 1 from article_tags at
                        join tags t on t.id = at.tag_id
                        where at.article_id = a.id and t.slug = :tag)
                    """);
            parameters.put("tag", filter.tag().trim());
        }
        if (StringUtils.hasText(filter.band())) {
            where.append("""
                     and exists (
                        select 1 from article_bands ab
                        join bands b on b.id = ab.band_id
                        where ab.article_id = a.id and b.slug = :band)
                    """);
            parameters.put("band", filter.band().trim());
        }
        if (StringUtils.hasText(filter.q())) {
            where.append(" and a.search_vector @@ plainto_tsquery('spanish', :q)");
            parameters.put("q", filter.q().trim());
        }
        return where.toString();
    }

    private String buildOrderBy(ArticleFilter filter) {
        if (filter.sort() == ArticleSort.RELEVANCE && StringUtils.hasText(filter.q())) {
            return " order by ts_rank(a.search_vector, plainto_tsquery('spanish', :q)) desc,"
                    + " a.published_at desc, a.id desc";
        }
        if (filter.sort() == ArticleSort.OLDEST) {
            return " order by a.published_at asc, a.id asc";
        }
        return " order by a.published_at desc, a.id desc";
    }

    private void bind(Query query, Map<String, Object> parameters) {
        parameters.forEach(query::setParameter);
    }
}
