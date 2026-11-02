package com.example.users.common;

public class InvalidQueryParameterException extends RuntimeException {

    public InvalidQueryParameterException(String parameter, String value, String allowedValues) {
        super("Invalid value '%s' for parameter '%s'. Allowed values: %s".formatted(value, parameter, allowedValues));
    }
}
