package de.conduit.articles;

import de.conduit.articles.dto.ArticleView;
import de.conduit.articles.dto.CreateArticleCommand;

import java.util.List;
import java.util.UUID;

public interface ArticleService {
    ArticleView create(UUID authorID, CreateArticleCommand command);
    ArticleView getBySlug(String slug);
    List<String> listTags();
}
