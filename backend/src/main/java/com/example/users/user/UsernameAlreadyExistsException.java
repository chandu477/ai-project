package com.example.users.user;

import com.example.users.common.ApiException;
import org.springframework.http.HttpStatus;

public class UsernameAlreadyExistsException extends ApiException {

    public UsernameAlreadyExistsException(String username) {
        super(HttpStatus.CONFLICT, "Username already exists", "Username '%s' is already taken".formatted(username));
    }
}
