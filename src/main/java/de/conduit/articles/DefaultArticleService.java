package de.conduit.articles;

import de.conduit.articles.exception.ArticleNotFoundException;
import de.conduit.articles.dto.ArticleView;
import de.conduit.articles.exception.AuthorAccountMissingException;
import de.conduit.articles.dto.CreateArticleCommand;
import de.conduit.users.AuthorProfiles;
import de.conduit.users.AuthorProfiles.AuthorProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.conduit.articles.dto.UpdateArticleCommand;
import de.conduit.articles.exception.ArticleAccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import de.conduit.articles.dto.ArticleListView;
import de.conduit.articles.dto.ListArticlesQuery;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DefaultArticleService implements ArticleService {
    private final ArticleRepository articles;
    private final AuthorProfiles authors;
    private final Clock clock;
    private final ArticleQueries articleQueries;

    public DefaultArticleService(ArticleRepository articles, AuthorProfiles authors,
                                 Clock clock, ArticleQueries articleQueries) {
        this.articles = articles;
        this.authors = authors;
        this.clock = clock;
        this.articleQueries = articleQueries;
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
        return view(article, author);
    }

    @Override
    @Transactional(readOnly = true)
    public ArticleView getBySlug(String slug) {
        Article article = articles.findBySlug(slug).orElseThrow(ArticleNotFoundException::new);
        AuthorProfile author = authors.findById(article.getAuthorId()).orElseThrow(AuthorAccountMissingException::new);

        return view(article, author);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listTags() {
        return articles.findAllTags();
    }

    private static ArticleView view(Article article, AuthorProfile author) {
        return new ArticleView(
                article.getSlug(),
                article.getTitle(),
                article.getDescription(),
                article.getBody(),
                article.getTagList(),
                article.getCreatedAt(),
                article.getUpdatedAt(),
                new ArticleView.AuthorView(
                        author.username(), author.bio(), author.image(), false
                ),
                false,
                0,
                List.of()
        );
    }

    @Override
    @Transactional
    public ArticleView update(UUID actorID, String slug, UpdateArticleCommand command) {
        Objects.requireNonNull(command, "command is required");
        Article article = requireOwnedArticle(actorID, slug);

        article.updateContent(command.title(), command.description(), command.body(), Instant.now(clock));
        articles.flush();
        AuthorProfile author = authors.findById(article.getAuthorId()).orElseThrow(AuthorAccountMissingException::new);
        return view(article, author);
    }

    @Override
    @Transactional
    public void delete(UUID actorID, String slug) {
        Article article = requireOwnedArticle(actorID, slug);
        articles.delete(article);
        articles.flush();
    }


    private Article requireOwnedArticle(UUID actorID, String slug) {
        Objects.requireNonNull(actorID, "actorID is required");
        Article article = articles.findBySlug(slug).orElseThrow(ArticleNotFoundException::new);
        if (!article.isAuthoredBy(actorID)) {
            throw new ArticleAccessDeniedException("You must be an owner!");
        }

        return article;
    }

    @Override
    @Transactional(readOnly = true)
    public ArticleListView list(ListArticlesQuery query) {
        Objects.requireNonNull(query, "query is required");
        UUID authorID = null;
        if (query.author() != null) {
            AuthorProfile author = authors.findByUsername(query.author()).orElse(null);
            if (author == null) {
                return new ArticleListView(List.of(), 0);
            }
            authorID = author.id();
        }
        long total = articleQueries.count(query.tag(), authorID);
        if (query.offset() >= total) {
            return new ArticleListView(List.of(), total);
        }
        List<Article> page = articleQueries.findPage(query.tag(), authorID, query.limit(), query.offset());

        Set<UUID> authorIDs = page.stream()
                .map(Article::getAuthorId)
                .collect(Collectors.toSet());

        Map<UUID, AuthorProfile> profiles = authors.findByIDs(authorIDs);

        List<ArticleView> views = page.stream().map(article -> {
            AuthorProfile author = profiles.get(article.getAuthorId());
            if (author == null) {
                throw new IllegalStateException("Article author is missing");
            }
            return view(article, author);
        }).toList();

        return new ArticleListView(views, total);
    }
}
