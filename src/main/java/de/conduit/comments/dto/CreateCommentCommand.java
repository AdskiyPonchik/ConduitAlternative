
package de.conduit.comments.dto;

import de.conduit.comments.Comment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentCommand(
        @NotBlank @Size(max = Comment.BODY_MAX_LENGTH) String body
) {
}
