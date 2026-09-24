package de.conduit.articles.exception;

public class ArticleAccessDeniedException extends RuntimeException {
    public ArticleAccessDeniedException(String message) {
        super("Only the author can change or delete this article");
    }
}
