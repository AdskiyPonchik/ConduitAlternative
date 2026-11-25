package de.conduit.users;

import de.conduit.users.exception.CurrentUserNotFoundException;
import de.conduit.users.exception.EmailAlreadyTakenException;
import de.conduit.users.exception.InvalidCredentialsException;
import de.conduit.users.exception.InvalidFollowException;
import de.conduit.users.exception.InvalidUserUpdateException;
import de.conduit.users.exception.ProfileNotFoundException;
import de.conduit.users.exception.UsernameAlreadyTakenException;
import de.conduit.web.ApiExceptionHandler;
import de.conduit.web.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice(assignableTypes = {UserController.class, ProfileController.class})
public class UserExceptionHandler extends ApiExceptionHandler {

    /**
     * Raised by method validation on {@code @Validated} {@link DefaultUserService}.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse serviceValidation(ConstraintViolationException exception) {
        List<String> messages = exception.getConstraintViolations().stream()
                .map(violation -> lastPropertyName(violation) + ": " + violation.getMessage())
                .sorted()
                .toList();
        return ErrorResponse.of(messages);
    }

    @ExceptionHandler({InvalidFollowException.class, InvalidUserUpdateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse badRequest(RuntimeException exception) {
        return error(exception.getMessage());
    }

    @ExceptionHandler({InvalidCredentialsException.class, CurrentUserNotFoundException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse unauthorized(RuntimeException exception) {
        return error(exception.getMessage());
    }

    @ExceptionHandler(ProfileNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse profileNotFound(ProfileNotFoundException exception) {
        return error(exception.getMessage());
    }

    @ExceptionHandler({UsernameAlreadyTakenException.class, EmailAlreadyTakenException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse alreadyTaken(RuntimeException exception) {
        return error(exception.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse concurrentUpdate() {
        return error("User was changed by another request. Reload it and try again.");
    }

    private static String lastPropertyName(ConstraintViolation<?> violation) {
        String field = "request";
        for (Path.Node node : violation.getPropertyPath()) {
            if (node.getKind() == ElementKind.PROPERTY) {
                field = node.getName();
            }
        }
        return field;
    }
}
