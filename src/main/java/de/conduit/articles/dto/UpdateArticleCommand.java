package de.conduit.articles.dto;

import de.conduit.articles.Article;
import jakarta.validation.constraints.Size;


public record UpdateArticleCommand(
        @Size(max = Article.TITLE_MAX_LENGTH) String title,
        @Size(max = Article.DESCRIPTION_MAX_LENGTH) String description,
        @Size(max = Article.BODY_MAX_LENGTH) String body) {
    public UpdateArticleCommand {
        title = title == null ? null : title.strip();
        description = description == null ? null : description.strip();
    }
}
