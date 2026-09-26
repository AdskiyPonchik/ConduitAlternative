package de.conduit.comments;

import de.conduit.comments.dto.CommentListView;
import de.conduit.comments.dto.CommentView;
import de.conduit.comments.dto.CreateCommentCommand;
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
@RequestMapping("/api/articles/{slug}/comments")
@Tag(name = "Comments")
public class CommentController {
    private final CommentService comments;

    public CommentController(CommentService comments) {
        this.comments = comments;
    }

    @GetMapping({"", "/"})
    @Operation(summary = "List comments")
    public CommentListView list(@PathVariable("slug") String slug) {
        return comments.list(slug);
    }

    @PostMapping({"", "/"})
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Add a comment")
    @SecurityRequirement(name = "tokenAuth")
    public CommentEnvelope create(
            @PathVariable("slug") String slug,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return new CommentEnvelope(comments.create(actorId, slug, request.comment()));
    }

    @DeleteMapping({"/{commentId}", "/{commentId}/"})
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Delete a comment")
    @SecurityRequirement(name = "tokenAuth")
    public void delete(
            @PathVariable("slug") String slug,
            @PathVariable("commentId") Integer commentId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        comments.delete(UUID.fromString(jwt.getSubject()), slug, commentId);
    }

    public record CreateCommentRequest(@NotNull @Valid CreateCommentCommand comment) {
    }

    public record CommentEnvelope(CommentView comment) {
    }
}