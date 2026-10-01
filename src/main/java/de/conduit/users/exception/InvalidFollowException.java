package de.conduit.users.exception;

public class InvalidFollowException extends RuntimeException {
    public InvalidFollowException() {
        super("You cannot follow or unfollow yourself");
    }
}