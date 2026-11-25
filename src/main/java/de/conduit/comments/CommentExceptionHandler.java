package de.conduit.comments;

import de.conduit.articles.exception.ArticleNotFoundException;
import de.conduit.comments.exception.CommentAccessDeniedException;
import de.conduit.comments.exception.CommentNotFoundException;
import de.conduit.comments.exception.CommentUserNotFoundException;
import de.conduit.comments.exception.InvalidCommentContentException;
import de.conduit.web.ApiExceptionHandler;
import de.conduit.web.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = CommentController.class)
public class CommentExceptionHandler extends ApiExceptionHandler {

    @ExceptionHandler(InvalidCommentContentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidContent(InvalidCommentContentException exception) {
        return error(exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidId() {
        return error("Comment id must be a valid integer");
    }

    @ExceptionHandler(CommentUserNotFoundException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse missingUser(CommentUserNotFoundException exception) {
        return error(exception.getMessage());
    }

    @ExceptionHandler(CommentAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse forbidden(CommentAccessDeniedException exception) {
        return error(exception.getMessage());
    }

    @ExceptionHandler({ArticleNotFoundException.class, CommentNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(RuntimeException exception) {
        return error(exception.getMessage());
    }
}
