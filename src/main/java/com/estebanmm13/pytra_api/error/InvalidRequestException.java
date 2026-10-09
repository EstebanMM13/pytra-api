package com.estebanmm13.pytra_api.error;

/** A well-formed request the API refuses (400); the message is a stable code clients can rely on. */
public class InvalidRequestException extends RuntimeException {

    public static final String INVALID_YEAR = "INVALID_YEAR";
    public static final String INVALID_EXPORT_FORMAT = "INVALID_EXPORT_FORMAT";
    public static final String CONFIRMATION_MISMATCH = "CONFIRMATION_MISMATCH";
    public static final String INVALID_PASSWORD = "INVALID_PASSWORD";

    public InvalidRequestException(String code) {
        super(code);
    }
}
