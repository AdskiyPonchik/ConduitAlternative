package de.conduit.users;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public class UserFollows implements Following {
    private final EntityManager entityManager;

    public UserFollows(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void add(UUID followerId, UUID followedId) {
        entityManager.createNativeQuery("""
                        insert into user_follows (follower_id, followed_id)
                        values (:followerId, :followedId)
                        on conflict (follower_id, followed_id) do nothing
                        """)
                .setParameter("followerId", followerId)
                .setParameter("followedId", followedId)
                .executeUpdate();
    }

    public void remove(UUID followerId, UUID followedId) {
        entityManager.createNativeQuery("""
                        delete from user_follows
                        where follower_id = :followerId and followed_id = :followedId
                        """)
                .setParameter("followerId", followerId)
                .setParameter("followedId", followedId)
                .executeUpdate();
    }

    @Override
    public Set<UUID> findFollowedIds(UUID viewerId, Set<UUID> authorIds) {
        if (viewerId == null || authorIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> ids = entityManager.createNativeQuery("""
                        select followed_id from user_follows
                        where follower_id = :viewerId and followed_id in (:authorIds)
                        """, UUID.class)
                .setParameter("viewerId", viewerId)
                .setParameter("authorIds", authorIds)
                .getResultList();
        return Set.copyOf(ids);
    }
}
