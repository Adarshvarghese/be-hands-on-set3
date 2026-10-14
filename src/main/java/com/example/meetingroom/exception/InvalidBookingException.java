package com.example.meetingroom.exception;

import java.util.LinkedHashMap;
import java.util.Map;

public class InvalidBookingException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public InvalidBookingException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors != null ? fieldErrors : new LinkedHashMap<>();
    }

    public InvalidBookingException(String field, String message) {
        super(message);
        this.fieldErrors = new LinkedHashMap<>();
        this.fieldErrors.put(field, message);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
