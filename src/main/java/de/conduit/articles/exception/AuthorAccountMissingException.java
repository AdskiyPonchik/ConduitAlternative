package de.conduit.articles.exception;

public class AuthorAccountMissingException extends RuntimeException {
    public AuthorAccountMissingException() {
        super("Article references a missing author");
    }
}
