package de.conduit.comments.dto;

import java.util.List;

public record CommentListView(List<CommentView> comments) {
    public CommentListView {
        comments = List.copyOf(comments);
    }
}
