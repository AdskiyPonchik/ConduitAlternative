package de.conduit.comments;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, Integer> {
    List<Comment> findByArticleIdOrderByCreatedAtDescIdDesc(UUID articleId);

    Optional<Comment> findByIdAndArticleId(Integer id, UUID articleId);
}