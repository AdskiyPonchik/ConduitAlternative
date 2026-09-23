package de.conduit.articles;


import de.conduit.articles.exception.ArticleNotFoundException;
import de.conduit.articles.exception.AuthorAccountMissingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {ArticleController.class, TagController.class})
public class ArticleExceptionHandler {
    @ExceptionHandler(ArticleNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(ArticleNotFoundException exception) {
        return error(exception.getMessage());
    }

    @ExceptionHandler(AuthorAccountMissingException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse missingAuthor(AuthorAccountMissingException exception) {
        return error(exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidRequest(MethodArgumentNotValidException exception) {
        List<String> messages = exception.getBindingResult().getFieldErrors()
                .stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .sorted()
                .toList();
        return new ErrorResponse(Map.of("body", messages));
    }


    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse unreadableBody() {
        return error("Request body must contain valid JSON in the expected format");
    }

    private static ErrorResponse error(String message) {
        return new ErrorResponse(Map.of("body", List.of(message)));
    }

    public record ErrorResponse(Map<String, List<String>> errors) {
    }
}
