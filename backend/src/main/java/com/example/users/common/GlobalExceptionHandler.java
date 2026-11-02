package com.example.users.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Application-specific errors. Framework errors (validation, type mismatch) are rendered as
 * ProblemDetail by Spring Boot via spring.mvc.problemdetails.enabled.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidQueryParameterException.class)
    public ProblemDetail handleInvalidQueryParameter(InvalidQueryParameterException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid query parameter");
        return problem;
    }
}
