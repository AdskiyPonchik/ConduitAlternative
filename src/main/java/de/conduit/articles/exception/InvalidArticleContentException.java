package de.conduit.articles.exception;

public class InvalidArticleContentException extends RuntimeException {
    public InvalidArticleContentException(String message) {
        super(message);
    }
}
