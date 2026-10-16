package com.veritasvault.exception;

/**
 * Thrown when a request conflicts with the current state of a resource,
 * such as double-booking collisions, duplicate unique identifiers,
 * or concurrent state conflicts.
 * Maps to HTTP 409 CONFLICT.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
