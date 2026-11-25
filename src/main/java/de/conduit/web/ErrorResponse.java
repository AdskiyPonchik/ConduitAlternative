package de.conduit.web;

import java.util.List;
import java.util.Map;

/**
 * RealWorld error body: {@code {"errors":{"body":["..."]}}}.
 */
public record ErrorResponse(Map<String, List<String>> errors) {

    public static ErrorResponse of(String message) {
        return of(List.of(message));
    }

    public static ErrorResponse of(List<String> messages) {
        return new ErrorResponse(Map.of("body", messages));
    }
}
