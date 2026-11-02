package com.example.users.salary;

import com.example.users.common.ApiException;
import org.springframework.http.HttpStatus;

public class NoSalaryDataException extends ApiException {

    public NoSalaryDataException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Not enough data", "There are no users yet, so a salary cannot be recommended");
    }
}
