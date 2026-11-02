package com.example.users.common;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Application-specific errors. Extending ResponseEntityExceptionHandler makes this the single advice
 * that renders framework errors as ProblemDetail (Boot's default advice backs off), so request body
 * validation can carry per-field messages in an "errors" property.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String INVALID_REQUEST_TITLE = "Invalid request";
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([A-Z])");

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle(INVALID_REQUEST_TITLE);
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Optional<String> wholeNumberField = wholeNumberField(ex);
        ProblemDetail problem = wholeNumberField.isPresent()
                ? ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed")
                : ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
        problem.setTitle(INVALID_REQUEST_TITLE);
        wholeNumberField.ifPresent(field ->
                problem.setProperty("errors", Map.of(field, humanize(field) + " must be a whole number")));
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** The field of an integer property that received something else, such as 7.9 or "abc". */
    private static Optional<String> wholeNumberField(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof MismatchedInputException mismatch
                && !mismatch.getPath().isEmpty()
                && isIntegerType(mismatch.getTargetType())) {
            return Optional.ofNullable(mismatch.getPath().getLast().getFieldName());
        }
        return Optional.empty();
    }

    private static boolean isIntegerType(Class<?> type) {
        return Integer.class.equals(type) || int.class.equals(type);
    }

    /** yearsOfExperience becomes "Years of experience". */
    private static String humanize(String field) {
        String spaced = CAMEL_BOUNDARY.matcher(field).replaceAll(" $1").toLowerCase(Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setTitle(ex.getTitle());
        return problem;
    }
}
