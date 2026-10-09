package com.estebanmm13.pytra_api.error;

import lombok.Getter;

/** Per-user throttle hit (429); the message is a stable code. */
@Getter
public class TooManyRequestsException extends RuntimeException {

    public static final String EXPORT_RATE_LIMITED = "EXPORT_RATE_LIMITED";

    private final long retryAfterSeconds;

    public TooManyRequestsException(String code, long retryAfterSeconds) {
        super(code);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
