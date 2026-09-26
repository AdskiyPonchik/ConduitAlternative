package de.conduit.comments.exception;

public class CommentAccessDeniedException extends RuntimeException {
    public CommentAccessDeniedException() {
        super("Only the author can delete this comment");
    }
}