package de.conduit.articles;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
public class ArticleFavorites {
    private final EntityManager entityManager;

    public ArticleFavorites(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void add(UUID articleId, UUID userId) {
        entityManager.createNativeQuery("""
                        insert into article_favorites (article_id, user_id)
                        values (:articleId, :userId)
                        on conflict (article_id, user_id) do nothing
                        """)
                .setParameter("articleId", articleId)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    public void remove(UUID articleId, UUID userId) {
        entityManager.createNativeQuery("""
                        delete from article_favorites
                        where article_id = :articleId and user_id = :userId
                        """)
                .setParameter("articleId", articleId)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    public FavoriteState findState(UUID articleID, UUID viewerID) {
        return findStates(Set.of(articleID), viewerID).getOrDefault(articleID, FavoriteState.NONE);
    }

    public Map<UUID, FavoriteState> findStates(Set<UUID> articleIds, UUID viewerId) {
        if (articleIds.isEmpty()) {
            return Map.of();
        }

        String viewerExpression = viewerId == null
                ? "false"
                : "bool_or(user_id = :viewerId)";
        var query = entityManager.createNativeQuery("""
                select article_id, count(*), %s
                from article_favorites
                where article_id in (:articleIds)
                group by article_id
                """.formatted(viewerExpression), Object[].class);
        query.setParameter("articleIds", articleIds);
        if (viewerId != null) {
            query.setParameter("viewerId", viewerId);
        }

        List<Object[]> rows = query.getResultList();
        Map<UUID, FavoriteState> result = new HashMap<>();
        for (Object[] row : rows) {
            UUID articleId = (UUID) row[0];
            long count = ((Number) row[1]).longValue();
            boolean favorited = Boolean.TRUE.equals(row[2]);
            result.put(articleId, new FavoriteState(count, favorited));
        }
        return Map.copyOf(result);
    }

    public record FavoriteState(long count, boolean favorited) {
        public static final FavoriteState NONE = new FavoriteState(0, false);
    }


}
