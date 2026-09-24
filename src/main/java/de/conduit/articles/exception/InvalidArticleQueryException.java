package de.conduit.articles.exception;

public class InvalidArticleQueryException extends RuntimeException {
    public InvalidArticleQueryException(String message) {
        super(message);
    }
}
