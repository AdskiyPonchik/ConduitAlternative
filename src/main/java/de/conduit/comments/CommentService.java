package de.conduit.comments;

import de.conduit.comments.dto.CommentListView;
import de.conduit.comments.dto.CommentView;
import de.conduit.comments.dto.CreateCommentCommand;

import java.util.UUID;

public interface CommentService {
    CommentView create(UUID actorId, String slug, CreateCommentCommand command);

    CommentListView list(String slug, UUID viewerId);

    void delete(UUID actorId, String slug, Integer commentId);
}
