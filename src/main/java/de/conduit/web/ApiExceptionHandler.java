package de.conduit.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * Handlers shared by every feature's {@code @RestControllerAdvice}.
 * <p>
 * This class is not an advice itself. Each feature handler extends it and keeps its own
 * {@code assignableTypes} scope; Spring also discovers {@code @ExceptionHandler} methods
 * declared on the superclass. A subclass must not map the same exception types again,
 * otherwise Spring rejects the advice as ambiguous at startup.
 */
public abstract class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidRequest(MethodArgumentNotValidException exception) {
        List<String> messages = exception.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .sorted()
                .toList();
        return ErrorResponse.of(messages);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse unreadableBody() {
        return error("Request body must contain valid JSON in the expected format");
    }

    protected static ErrorResponse error(String message) {
        return ErrorResponse.of(message);
    }
}
