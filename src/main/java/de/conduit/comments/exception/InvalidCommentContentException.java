package de.conduit.comments.exception;

public class InvalidCommentContentException extends RuntimeException {
    public InvalidCommentContentException(String message) {
        super(message);
    }
}