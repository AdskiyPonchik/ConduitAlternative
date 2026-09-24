package de.conduit.articles;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class ArticleQueries {
    private final EntityManager entityManager;

    public ArticleQueries(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public long count(String tag, UUID authorID) {
        return filteredQuery("select count(a)", Long.class, tag, authorID, "").getSingleResult();
    }


    public List<Article> findPage(String tag, UUID authorID, int limit, int offset) {
        List<UUID> ids = filteredQuery("select a.id", UUID.class, tag, authorID, " order by a.createdAt desc, a.id desc")
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
        if (ids.isEmpty()) {
            return List.of();
        }

        List<Article> loaded = entityManager.createQuery("""
                        select distinct a from Article a left join fetch a.tags
                        where a.id in :ids""", Article.class).setParameter("ids", ids)
                .getResultList();
        var byID = loaded.stream().collect(Collectors.toMap(Article::getId, Function.identity()));
        return ids.stream()
                .map(byID::get)
                .filter(Objects::nonNull)
                .toList();
    }


    private <T> TypedQuery<T> filteredQuery(
            String selection, Class<T> resultType,
            String tag, UUID authorID, String ordering) {
        String jpql = selection + " from Article a where 1=1";
        if (tag != null) {
            jpql += " and :tag member of a.tags";
        }
        if (authorID != null) {
            jpql += " and a.authorId = :authorID";
        }

        TypedQuery<T> query = entityManager.createQuery(jpql + ordering, resultType);
        if (tag != null) {
            query.setParameter("tag", tag);
        }
        if (authorID != null) {
            query.setParameter("authorID", authorID);
        }
        return query;
    }
}
