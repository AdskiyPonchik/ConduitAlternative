package de.conduit.identity.internal.application;

public class CurrentUserNotFoundException extends RuntimeException {
    public CurrentUserNotFoundException() {
        super("Current user no longer exists");
    }
}
