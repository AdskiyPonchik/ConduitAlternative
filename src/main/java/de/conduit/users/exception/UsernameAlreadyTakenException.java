package de.conduit.users.exception;

public class UsernameAlreadyTakenException extends RuntimeException{

    public UsernameAlreadyTakenException(){
        super("Username is already taken");
    }

    public UsernameAlreadyTakenException(Throwable cause) {
        super("Username is already taken", cause);
    }

}
