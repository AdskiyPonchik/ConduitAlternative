package de.conduit.articles;

import de.conduit.articles.exception.ArticleAccessDeniedException;
import de.conduit.articles.exception.ArticleNotFoundException;
import de.conduit.articles.exception.AuthorAccountMissingException;
import de.conduit.articles.exception.FavoriteUserNotFoundException;
import de.conduit.articles.exception.InvalidArticleContentException;
import de.conduit.articles.exception.InvalidArticleQueryException;
import de.conduit.web.ApiExceptionHandler;
import de.conduit.web.ErrorResponse;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {ArticleController.class, TagController.class})
public class ArticleExceptionHandler extends ApiExceptionHandler {

    @ExceptionHandler({InvalidArticleQueryException.class, InvalidArticleContentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse badRequest(RuntimeException exception) {
        return error(exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidParameterType() {
        return error("Query parameters have invalid values: limit and offset must be integers");
    }

    @ExceptionHandler({AuthorAccountMissingException.class, FavoriteUserNotFoundException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse missingAccount(RuntimeException exception) {
        return error(exception.getMessage());
    }

    @ExceptionHandler(ArticleAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse forbidden(ArticleAccessDeniedException exception) {
        return error(exception.getMessage());
    }

    @ExceptionHandler(ArticleNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(ArticleNotFoundException exception) {
        return error(exception.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse concurrentChange() {
        return error("Article was changed by another request. Reload it and try again.");
    }
}
