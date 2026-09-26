package de.conduit.comments.dto;

import java.time.Instant;

public record CommentView(
        Integer id,
        Instant createdAt,
        Instant updatedAt,
        String body,
        AuthorView author
) {
    public record AuthorView(String username, String bio, String image, boolean following) {
    }
}