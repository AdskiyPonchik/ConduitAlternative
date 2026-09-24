package de.conduit.articles;


import de.conduit.articles.exception.ArticleNotFoundException;
import de.conduit.articles.exception.AuthorAccountMissingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import de.conduit.articles.exception.ArticleAccessDeniedException;
import de.conduit.articles.exception.InvalidArticleContentException;
import org.springframework.dao.OptimisticLockingFailureException;
import de.conduit.articles.exception.InvalidArticleQueryException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    @ExceptionHandler(ArticleAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse forbidden(ArticleAccessDeniedException exception) {
        return error(exception.getMessage());
    }

    @ExceptionHandler(InvalidArticleQueryException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidQuery(InvalidArticleQueryException exception) {
        return error(exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidParameterType() {
        return error("Query parameters have invalid values: limit and offset must be integers");
    }

    @ExceptionHandler(InvalidArticleContentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidContent(InvalidArticleContentException exception) {
        return error(exception.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse concurrentChange() {
        return error("Article was changed by another request. Reload it and try again.");
    }

    private static ErrorResponse error(String message) {
        return new ErrorResponse(Map.of("body", List.of(message)));
    }

    public record ErrorResponse(Map<String, List<String>> errors) {
    }
}
