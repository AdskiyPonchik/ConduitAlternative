package de.conduit.articles.dto;

import java.time.Instant;
import java.util.List;

public record ArticleView(
        String slug,
        String title,
        String description,
        String body,
        List<String> tagList,
        Instant createdAt,
        Instant updatedAt,
        AuthorView author,
        boolean favorited,
        int favoritesCount,
        List<String> images
) {
    public record AuthorView(
            String username,
            String bio,
            String image,
            boolean following
    ) {
    }
}
