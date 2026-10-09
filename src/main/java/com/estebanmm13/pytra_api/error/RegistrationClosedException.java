package com.estebanmm13.pytra_api.error;

/** Thrown when registration is invite-only and the email is not on the allowlist. */
public class RegistrationClosedException extends RuntimeException {

    /** Stable message clients can rely on (the HTTP status is 403). */
    public static final String CODE = "REGISTRATION_INVITE_ONLY";

    public RegistrationClosedException() {
        super(CODE);
    }
}
