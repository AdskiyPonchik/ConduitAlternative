package de.conduit.articles;

import de.conduit.articles.exception.ArticleNotFoundException;
import de.conduit.articles.dto.ArticleView;
import de.conduit.articles.exception.AuthorAccountMissingException;
import de.conduit.articles.dto.CreateArticleCommand;
import de.conduit.users.AuthorProfiles;
import de.conduit.users.AuthorProfiles.AuthorProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class DefaultArticleService implements ArticleService {
    private final ArticleRepository articles;
    private final AuthorProfiles authors;
    private final Clock clock;

    public DefaultArticleService(ArticleRepository articles, AuthorProfiles authors, Clock clock) {
        this.articles = articles;
        this.authors = authors;
        this.clock = clock;
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
}
