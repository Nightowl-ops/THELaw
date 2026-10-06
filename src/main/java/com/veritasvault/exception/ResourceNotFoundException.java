package com.veritasvault.exception;

///this is a summary a customer unchecked runtime exception thrown
/// whenever an entity lookup fails
/// example: when looking up a user ID ,case ID, or evidence item that doesnt exist in the databse


public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}