package de.conduit.articles.exception;

public class ArticleNotFoundException extends RuntimeException {
    public ArticleNotFoundException() {
        super("Article not found");
    }
}
