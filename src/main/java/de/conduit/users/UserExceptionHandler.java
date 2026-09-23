package de.conduit.users;

import de.conduit.users.exception.CurrentUserNotFoundException;
import de.conduit.users.exception.EmailAlreadyTakenException;
import de.conduit.users.exception.UsernameAlreadyTakenException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import de.conduit.users.exception.InvalidCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestControllerAdvice(assignableTypes = UserController.class)
public class UserExceptionHandler {

    @ExceptionHandler({
            UsernameAlreadyTakenException.class,
            EmailAlreadyTakenException.class
    })
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(RuntimeException exception) {
        return error(exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleRequestValidation(
            MethodArgumentNotValidException exception
    ) {
        List<String> messages = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField()
                        + ": " + error.getDefaultMessage())
                .sorted()
                .toList();

        return new ErrorResponse(Map.of("body", messages));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleServiceValidation(
            ConstraintViolationException exception
    ) {
        List<String> messages = exception.getConstraintViolations()
                .stream()
                .map(violation -> violation.getPropertyPath()
                        + ": " + violation.getMessage())
                .sorted()
                .toList();

        return new ErrorResponse(Map.of("body", messages));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadableBody() {
        return error("Request body must contain valid JSON "
                + "in the expected format");
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidCredentials(
            InvalidCredentialsException exception
    ) {
        return error(exception.getMessage());
    }

    private static ErrorResponse error(String message) {
        return new ErrorResponse(Map.of("body", List.of(message)));
    }

    @ExceptionHandler(CurrentUserNotFoundException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleMissingCurrentUser(
            CurrentUserNotFoundException exception
    ) {
        return error(exception.getMessage());
    }

    public record ErrorResponse(Map<String, List<String>> errors) {
    }
}