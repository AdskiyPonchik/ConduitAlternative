package de.conduit.articles;

import de.conduit.articles.dto.ArticleView;
import de.conduit.articles.dto.CreateArticleCommand;
import de.conduit.articles.dto.UpdateArticleCommand;
import de.conduit.articles.dto.ArticleListView;
import de.conduit.articles.dto.ListArticlesQuery;

import java.util.List;
import java.util.UUID;

public interface ArticleService {
    ArticleView create(UUID authorID, CreateArticleCommand command);

    ArticleView getBySlug(String slug, UUID viewerID);

    List<String> listTags();

    ArticleView update(UUID actorID, String slug, UpdateArticleCommand command);

    ArticleListView list(ListArticlesQuery query, UUID viewerID);

    void delete(UUID actorID, String slug);

    ArticleView favorite(UUID actorId, String slug);

    ArticleView unfavorite(UUID actorId, String slug);
}
