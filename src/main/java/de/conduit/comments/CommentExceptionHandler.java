package de.conduit.comments;

import de.conduit.articles.exception.ArticleNotFoundException;
import de.conduit.comments.exception.CommentAccessDeniedException;
import de.conduit.comments.exception.CommentNotFoundException;
import de.conduit.comments.exception.CommentUserNotFoundException;
import de.conduit.comments.exception.InvalidCommentContentException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

@RestControllerAdvice(assignableTypes = CommentController.class)
public class CommentExceptionHandler {
    @ExceptionHandler({ArticleNotFoundException.class, CommentNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(RuntimeException exception) {
        return error(exception.getMessage());
    }

    @ExceptionHandler(CommentAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse forbidden(CommentAccessDeniedException exception) {
        return error(exception.getMessage());
    }

    @ExceptionHandler(CommentUserNotFoundException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse missingUser(CommentUserNotFoundException exception) {
        return error(exception.getMessage());
    }

    @ExceptionHandler(InvalidCommentContentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidContent(InvalidCommentContentException exception) {
        return error(exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidRequest(MethodArgumentNotValidException exception) {
        List<String> messages = exception.getBindingResult().getFieldErrors().stream()
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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidId() {
        return error("Comment id must be a valid integer");
    }

    private static ErrorResponse error(String message) {
        return new ErrorResponse(Map.of("body", List.of(message)));
    }

    public record ErrorResponse(Map<String, List<String>> errors) {
    }
}
