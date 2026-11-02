package com.example.users.common;

import org.springframework.http.HttpStatus;

public class InvalidQueryParameterException extends ApiException {

    public InvalidQueryParameterException(String parameter, String value, String allowedValues) {
        super(HttpStatus.BAD_REQUEST, "Invalid query parameter", "Invalid value '%s' for parameter '%s'. Allowed values: %s".formatted(value, parameter, allowedValues));
    }
}
