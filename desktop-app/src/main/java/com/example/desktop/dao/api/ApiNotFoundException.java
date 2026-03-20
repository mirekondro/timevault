package com.example.desktop.dao.api;

/**
 * Signals that the backend API returned 404 for a requested resource.
 */
public class ApiNotFoundException extends RuntimeException {

    public ApiNotFoundException(String message) {
        super(message);
    }
}
