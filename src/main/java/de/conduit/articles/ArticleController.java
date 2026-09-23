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


    public record CreateArticleRequest(@NotNull @Valid CreateArticleCommand article) {
    }

    public record ArticleEnvelope(ArticleView article) {
    }
}
