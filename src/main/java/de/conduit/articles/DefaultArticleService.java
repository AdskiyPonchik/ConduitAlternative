package de.conduit.articles;

import de.conduit.articles.ArticleFavorites.FavoriteState;
import de.conduit.articles.dto.ArticleListView;
import de.conduit.articles.dto.ArticleView;
import de.conduit.articles.dto.CreateArticleCommand;
import de.conduit.articles.dto.ListArticlesQuery;
import de.conduit.articles.dto.UpdateArticleCommand;
import de.conduit.articles.exception.ArticleAccessDeniedException;
import de.conduit.articles.exception.ArticleNotFoundException;
import de.conduit.articles.exception.AuthorAccountMissingException;
import de.conduit.articles.exception.FavoriteUserNotFoundException;
import de.conduit.users.AuthorProfiles;
import de.conduit.users.AuthorProfiles.AuthorProfile;
import de.conduit.users.Following;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DefaultArticleService implements ArticleService {
    private final ArticleRepository articles;
    private final AuthorProfiles authors;
    private final Clock clock;
    private final ArticleQueries articleQueries;
    private final ArticleFavorites favorites;
    private final Following following;

    public DefaultArticleService(ArticleRepository articles, AuthorProfiles authors, Clock clock,
                                 ArticleQueries articleQueries, ArticleFavorites favorites, Following following) {
        this.articles = articles;
        this.authors = authors;
        this.clock = clock;
        this.articleQueries = articleQueries;
        this.favorites = favorites;
        this.following = following;
    }

    @Override
    @Transactional
    public ArticleView create(UUID authorID, CreateArticleCommand command) {
        Objects.requireNonNull(authorID, "authorId is required");
        Objects.requireNonNull(command, "command is required");

        AuthorProfile author = authors.findById(authorID)
                .orElseThrow(AuthorAccountMissingException::new);

        Article article = Article.create(
                UUID.randomUUID(),
                authorID,
                command.title(),
                command.description(),
                command.body(),
                command.tagList(),
                Instant.now(clock)
        );

        articles.saveAndFlush(article);
        return view(article, author, FavoriteState.NONE, false);
    }

    @Override
    @Transactional(readOnly = true)
    public ArticleView getBySlug(String slug, UUID viewerID) {
        Article article = articles.findBySlug(slug).orElseThrow(ArticleNotFoundException::new);
        return viewFor(article, requireAuthor(article), viewerID);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listTags() {
        return articles.findAllTags();
    }

    @Override
    @Transactional
    public ArticleView update(UUID actorID, String slug, UpdateArticleCommand command) {
        Objects.requireNonNull(command, "command is required");
        Article article = requireOwnedArticle(actorID, slug);

        article.updateContent(command.title(), command.description(), command.body(), Instant.now(clock));
        articles.flush();
        return viewFor(article, requireAuthor(article), actorID);
    }

    @Override
    @Transactional
    public void delete(UUID actorID, String slug) {
        Article article = requireOwnedArticle(actorID, slug);
        articles.delete(article);
        articles.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public ArticleListView list(ListArticlesQuery query, UUID viewerID) {
        Objects.requireNonNull(query, "query is required");
        UUID authorID = null;
        if (query.author() != null) {
            AuthorProfile author = authors.findByUsername(query.author()).orElse(null);
            if (author == null) {
                return new ArticleListView(List.of(), 0);
            }
            authorID = author.id();
        }
        UUID favoritedByID = null;
        if (query.favorited() != null) {
            AuthorProfile favoritingUser = authors.findByUsername(query.favorited()).orElse(null);
            if (favoritingUser == null) {
                return new ArticleListView(List.of(), 0);
            }
            favoritedByID = favoritingUser.id();
        }

        return loadPage(query, authorID, favoritedByID, null, viewerID);
    }

    @Override
    @Transactional(readOnly = true)
    public ArticleListView feed(UUID viewerId, int limit, int offset) {
        Objects.requireNonNull(viewerId, "viewerId is required");
        if (authors.findById(viewerId).isEmpty()) {
            throw new AuthorAccountMissingException();
        }
        ListArticlesQuery query = new ListArticlesQuery(null, null, null, limit, offset);
        return loadPage(query, null, null, viewerId, viewerId);
    }

    @Override
    @Transactional
    public ArticleView favorite(UUID actorID, String slug) {
        return changeFavorite(actorID, slug, true);
    }

    @Override
    @Transactional
    public ArticleView unfavorite(UUID actorID, String slug) {
        return changeFavorite(actorID, slug, false);
    }

    private ArticleView changeFavorite(UUID actorID, String slug, boolean add) {
        Objects.requireNonNull(actorID, "actorId is required");
        if (authors.findById(actorID).isEmpty()) {
            throw new FavoriteUserNotFoundException();
        }

        Article article = articles.findBySlugForUpdate(slug)
                .orElseThrow(ArticleNotFoundException::new);

        if (add) {
            favorites.add(article.getId(), actorID);
        } else {
            favorites.remove(article.getId(), actorID);
        }

        // The actor exists, so a missing author here is a data-integrity bug, not a client error.
        AuthorProfile author = authors.findById(article.getAuthorId())
                .orElseThrow(() -> new IllegalStateException("Article author is missing"));
        return viewFor(article, author, actorID);
    }

    private Article requireOwnedArticle(UUID actorID, String slug) {
        Objects.requireNonNull(actorID, "actorID is required");
        Article article = articles.findBySlug(slug).orElseThrow(ArticleNotFoundException::new);
        if (!article.isAuthoredBy(actorID)) {
            throw new ArticleAccessDeniedException("You must be an owner!");
        }
        return article;
    }

    private AuthorProfile requireAuthor(Article article) {
        return authors.findById(article.getAuthorId()).orElseThrow(AuthorAccountMissingException::new);
    }

    private ArticleListView loadPage(ListArticlesQuery query, UUID authorID,
                                     UUID favoritedByID, UUID followedByID, UUID viewerID) {
        long total = articleQueries.count(query.tag(), authorID, favoritedByID, followedByID);
        if (query.offset() >= total) {
            return new ArticleListView(List.of(), total);
        }
        List<Article> page = articleQueries.findPage(
                query.tag(), authorID, favoritedByID, followedByID, query.limit(), query.offset()
        );

        Set<UUID> authorIDs = page.stream()
                .map(Article::getAuthorId)
                .collect(Collectors.toSet());
        Set<UUID> articleIDs = page.stream()
                .map(Article::getId)
                .collect(Collectors.toSet());

        Map<UUID, AuthorProfile> profiles = authors.findByIDs(authorIDs);
        Set<UUID> followedIds = following.findFollowedIds(viewerID, authorIDs);
        Map<UUID, FavoriteState> states = favorites.findStates(articleIDs, viewerID);

        List<ArticleView> views = page.stream().map(article -> {
            AuthorProfile author = profiles.get(article.getAuthorId());
            if (author == null) {
                throw new IllegalStateException("Article author is missing");
            }
            return view(article, author,
                    states.getOrDefault(article.getId(), FavoriteState.NONE),
                    followedIds.contains(article.getAuthorId()));
        }).toList();

        return new ArticleListView(views, total);
    }

    /**
     * Single-article view personalized for {@code viewerID} (may be {@code null}).
     */
    private ArticleView viewFor(Article article, AuthorProfile author, UUID viewerID) {
        return view(article, author,
                favorites.findState(article.getId(), viewerID),
                isFollowing(viewerID, article.getAuthorId()));
    }

    private boolean isFollowing(UUID viewerId, UUID authorId) {
        return following.findFollowedIds(viewerId, Set.of(authorId)).contains(authorId);
    }

    private static ArticleView view(Article article, AuthorProfile author, FavoriteState state, boolean following) {
        return new ArticleView(
                article.getSlug(),
                article.getTitle(),
                article.getDescription(),
                article.getBody(),
                article.getTagList(),
                article.getCreatedAt(),
                article.getUpdatedAt(),
                new ArticleView.AuthorView(author.username(), author.bio(), author.image(), following),
                state.favorited(),
                state.count(),
                List.of()
        );
    }
}
