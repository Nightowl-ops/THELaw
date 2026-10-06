package com.veritasvault.exception;

/// example of when this will be used for example attempting to assign a non -attorny user as lead counsel or submitting
/// an invalid date range
///

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
