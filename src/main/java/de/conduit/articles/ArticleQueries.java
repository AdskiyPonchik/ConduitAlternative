package de.conduit.articles;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class ArticleQueries {
    private final EntityManager entityManager;

    public ArticleQueries (EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public long count(String tag, UUID authorId, UUID favoritedById) {
        Number total = (Number) filteredQuery(
                "select count(*)", Long.class,
                tag, authorId, favoritedById, ""
        ).getSingleResult();
        return total.longValue();
    }


    public List<Article> findPage(
            String tag, UUID authorId, UUID favoritedById, int limit, int offset
    ) {
        List<UUID> ids = filteredQuery(
                "select a.id", UUID.class,
                tag, authorId, favoritedById,
                " order by a.created_at desc, a.id desc"
        )
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
        if (ids.isEmpty()) {
            return List.of();
        }

        List<Article> loaded = entityManager.createQuery("""
                        select distinct a from Article a left join fetch a.tags
                        where a.id in :ids
                        """, Article.class)
                .setParameter("ids", ids)
                .getResultList();

        var byId = loaded.stream()
                .collect(Collectors.toMap(Article::getId, Function.identity()));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private Query filteredQuery(
            String selection, Class<?> resultType,
            String tag, UUID authorId, UUID favoritedById, String ordering
    ) {
        String sql = selection + " from articles a where 1 = 1";
        if (tag != null) {
            sql += """
                     and exists (
                        select 1 from article_tags t
                        where t.article_id = a.id and t.tag = :tag
                     )
                    """;
        }
        if (authorId != null) {
            sql += " and a.author_id = :authorId";
        }
        if (favoritedById != null) {
            sql += """
                     and exists (
                        select 1 from article_favorites f
                        where f.article_id = a.id and f.user_id = :favoritedById
                     )
                    """;
        }

        Query query = entityManager.createNativeQuery(sql + ordering, resultType);
        if (tag != null) {
            query.setParameter("tag", tag);
        }
        if (authorId != null) {
            query.setParameter("authorId", authorId);
        }
        if (favoritedById != null) {
            query.setParameter("favoritedById", favoritedById);
        }
        return query;
    }
}
