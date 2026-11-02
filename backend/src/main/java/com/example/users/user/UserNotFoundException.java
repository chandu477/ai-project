package com.example.users.user;

import com.example.users.common.ApiException;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApiException {

    public UserNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "User not found", "User %d not found".formatted(id));
    }
}
