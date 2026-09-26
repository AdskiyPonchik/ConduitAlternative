package de.conduit.articles.exception;

public class FavoriteUserNotFoundException extends RuntimeException {
    public FavoriteUserNotFoundException() {
        super("Current user no longer exists");
    }
}
