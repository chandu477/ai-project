package com.example.users.common;

import org.springframework.http.HttpStatus;

/** Base of application errors that map to an HTTP status and a ProblemDetail title. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    protected ApiException(HttpStatus status, String title, String message) {
        super(message);
        this.status = status;
        this.title = title;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }
}
