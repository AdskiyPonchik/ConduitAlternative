package de.conduit.articles.dto;

import de.conduit.articles.exception.InvalidArticleQueryException;

import java.util.Locale;


public record ListArticlesQuery(String tag, String author, String favorited, int limit, int offset) {
    public ListArticlesQuery {
        if (limit < 1 || limit > 100) {
            throw new InvalidArticleQueryException("Limit must be between 1 and 100");
        }

        if (offset < 0) {
            throw new InvalidArticleQueryException("Offset must not be negative");
        }

        tag = normalize(tag);
        if (tag != null) {
            tag = tag.toLowerCase(Locale.ROOT);
        }

        author = normalize(author);
        favorited = normalize(favorited);
    }


    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
