package com.insideinvoice.exception;

import com.insideinvoice.auth.dto.response.ApiResponse;

import java.util.Map;

/**
 * Error body for bean-validation failures: the standard ApiResponse envelope plus a
 * per-field error map. The frontend reads {@code response.data.fieldErrors} directly
 * (BusinessSetup.jsx, AdminAddUsers.jsx), so the key name is part of the API contract.
 */
public class ValidationErrorResponse extends ApiResponse<Void> {

    private final Map<String, String> fieldErrors;

    public ValidationErrorResponse(Map<String, String> fieldErrors) {
        this.fieldErrors = fieldErrors;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
