package de.conduit.comments;

import de.conduit.comments.exception.InvalidCommentContentException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "comments")
public class Comment {
    public static final int BODY_MAX_LENGTH = 5000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "article_id", nullable = false)
    private UUID articleId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Comment() {
    }

    public static Comment create(UUID articleId, UUID authorId, String body, Instant now) {
        if (body == null || body.isBlank()) {
            throw new InvalidCommentContentException("body must not be blank");
        }
        if (body.length() > BODY_MAX_LENGTH) {
            throw new InvalidCommentContentException("body is too long");
        }

        Comment comment = new Comment();
        comment.articleId = Objects.requireNonNull(articleId, "articleId is required");
        comment.authorId = Objects.requireNonNull(authorId, "authorId is required");
        comment.body = body;
        comment.createdAt = Objects.requireNonNull(now, "now is required")
                .truncatedTo(ChronoUnit.MICROS);
        comment.updatedAt = comment.createdAt;
        return comment;
    }

    public boolean isAuthoredBy(UUID userID) {
        return authorId.equals(userID);
    }

    public Integer getId() {
        return id;
    }

    public UUID getArticleId() {
        return articleId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
