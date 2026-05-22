package com.insideinvoice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class BusinessAccessViolationException extends RuntimeException {

    public BusinessAccessViolationException(String message) {
        super(message);
    }

    public BusinessAccessViolationException(Long expectedBusinessId, Long actualBusinessId) {
        super(String.format("Business access violation: expected businessId %d but user belongs to businessId %d",
                expectedBusinessId, actualBusinessId));
    }
}
