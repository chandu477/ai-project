package com.example.users.user;

import com.example.users.common.InvalidQueryParameterException;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Whitelist of sortable columns. Maps the public API parameter to the JPQL expression,
 * so client input never reaches the query directly.
 */
public enum UserSortField {
    USERNAME("username", "u.username"),
    ORDER_COUNT("orderCount", "COUNT(o)"),
    MONTHLY_SALARY("monthlySalary", "u.monthlySalary"),
    YEARS_OF_EXPERIENCE("yearsOfExperience", "u.yearsOfExperience");

    private static final String ALLOWED_PARAMS = Arrays.stream(values())
            .map(field -> field.param)
            .collect(Collectors.joining(", "));

    private final String param;
    private final String expression;

    UserSortField(String param, String expression) {
        this.param = param;
        this.expression = expression;
    }

    public String expression() {
        return expression;
    }

    public static UserSortField fromParam(String value) {
        return Arrays.stream(values())
                .filter(field -> field.param.equals(value))
                .findFirst()
                .orElseThrow(() -> new InvalidQueryParameterException("sort", value, ALLOWED_PARAMS));
    }
}
