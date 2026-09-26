package de.conduit.comments.exception;

public class CommentUserNotFoundException extends RuntimeException {
    public CommentUserNotFoundException() {
        super("Current user no longer exists");
    }
}