package de.conduit.articles;

import de.conduit.articles.dto.ArticleView;
import de.conduit.articles.dto.CreateArticleCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import de.conduit.articles.dto.UpdateArticleCommand;
import de.conduit.articles.dto.ArticleListView;
import de.conduit.articles.dto.ListArticlesQuery;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@RequestMapping("/api/articles")
@Tag(name = "Articles")
public class ArticleController {
    private final ArticleService articles;

    public ArticleController(ArticleService articles) {
        this.articles = articles;
    }

    @PostMapping({"", "/"})
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an article")
    @SecurityRequirement(name = "tokenAuth")
    public ArticleEnvelope create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateArticleRequest request) {
        UUID authorID = UUID.fromString(jwt.getSubject());
        return new ArticleEnvelope(articles.create(authorID, request.article()));
    }

    @GetMapping({"/{slug}", "/{slug}/"})
    @Operation(summary = "Get an article")
    public ArticleEnvelope get(@PathVariable("slug") String slug) {
        return new ArticleEnvelope(articles.getBySlug(slug));
    }

    @GetMapping({"", "/"})
    @Operation(summary = "List articles")
    public ArticleListView list(
            @RequestParam(name = "tag", required = false) String tag,
            @RequestParam(name = "author", required = false) String author,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset
    ) {
        return articles.list(new ListArticlesQuery(tag, author, limit, offset));
    }

    @PutMapping({"/{slug}", "/{slug}/"})
    @Operation(summary = "Update an article")
    @SecurityRequirement(name = "tokenAuth")
    public ArticleEnvelope update(
            @PathVariable("slug") String slug,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateArticleRequest request
    ) {
        UUID actorID = UUID.fromString(jwt.getSubject());
        return new ArticleEnvelope(articles.update(actorID, slug, request.article()));
    }

    @DeleteMapping({"/{slug}", "/{slug}/"})
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Delete an article")
    @SecurityRequirement(name = "tokenAuth")
    public void delete(
            @PathVariable("slug") String slug,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        articles.delete(actorId, slug);
    }


    public record CreateArticleRequest(@NotNull @Valid CreateArticleCommand article) {
    }

    public record UpdateArticleRequest(@NotNull @Valid UpdateArticleCommand article) {
    }

    public record ArticleEnvelope(ArticleView article) {
    }
}
