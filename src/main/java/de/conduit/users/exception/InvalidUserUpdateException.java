package de.conduit.users.exception;

public class InvalidUserUpdateException extends RuntimeException {
    public InvalidUserUpdateException(String message) {
        super(message);
    }
}
