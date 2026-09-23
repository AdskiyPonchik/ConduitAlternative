package de.conduit.users.exception;

public class CurrentUserNotFoundException extends RuntimeException {
    public CurrentUserNotFoundException() {
        super("Current user no longer exists");
    }
}
