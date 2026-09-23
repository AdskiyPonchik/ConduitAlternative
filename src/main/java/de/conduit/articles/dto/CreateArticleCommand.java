package de.conduit.articles.dto;

import de.conduit.articles.Article;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Locale;

public record CreateArticleCommand(
        @NotBlank @Size(max = Article.TITLE_MAX_LENGTH)
        String title,
        @NotBlank @Size(max = Article.DESCRIPTION_MAX_LENGTH)
        String description,
        @NotBlank @Size(max = Article.BODY_MAX_LENGTH)
        String body,
        @Size(max = Article.MAX_TAGS)
        List<@NotBlank @Size(max = Article.TAG_MAX_LENGTH) String> tagList
) {
    public CreateArticleCommand {
        title = title == null ? null : title.strip();
        description = description == null ? null : description.strip();
        tagList = tagList == null ? List.of() : tagList.stream()
                .map(tag -> tag == null ? null : tag.strip().toLowerCase(Locale.ROOT))
                .toList();
    }
}
