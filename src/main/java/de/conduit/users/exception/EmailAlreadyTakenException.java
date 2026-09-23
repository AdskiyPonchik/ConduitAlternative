package de.conduit.users.exception;


public class EmailAlreadyTakenException extends RuntimeException {
    public EmailAlreadyTakenException() {
        super("Email is already taken");
    }

    public EmailAlreadyTakenException(Throwable cause){
        super("Email is already taken", cause);
    }
}
