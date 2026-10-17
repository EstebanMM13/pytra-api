package com.estebanmm13.pytra_api.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Failure of the PlayStation Network integration with a stable {@link #getCode() code} clients can rely on.
 * The message never contains upstream details (requests carry the service account's NPSSO and tokens).
 *
 * <p>A failing service account (expired NPSSO) is reported to users as {@link #UNAVAILABLE}, never as an
 * auth error: only admins see it, through the status endpoint and the WARN logs.
 */
@Getter
public class PsnIntegrationException extends RuntimeException {

    public static final String NOT_CONFIGURED = "PSN_NOT_CONFIGURED";
    public static final String NOT_LINKED = "PSN_NOT_LINKED";
    public static final String LINK_CHANGED = "PSN_LINK_CHANGED";
    public static final String INVALID_ONLINE_ID = "PSN_INVALID_ONLINE_ID";
    public static final String USER_NOT_FOUND = "PSN_USER_NOT_FOUND";
    public static final String RATE_LIMITED = "PSN_RATE_LIMITED";
    public static final String UNAVAILABLE = "PSN_UNAVAILABLE";
    /** Status-only code (admins): the service account's NPSSO was rejected; never thrown to clients. */
    public static final String SERVICE_AUTH_FAILED = "PSN_SERVICE_AUTH_FAILED";

    private final HttpStatus status;
    private final String code;

    public PsnIntegrationException(HttpStatus status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }

    public static PsnIntegrationException notConfigured() {
        return new PsnIntegrationException(HttpStatus.SERVICE_UNAVAILABLE, NOT_CONFIGURED);
    }

    public static PsnIntegrationException notLinked() {
        return new PsnIntegrationException(HttpStatus.NOT_FOUND, NOT_LINKED);
    }

    public static PsnIntegrationException linkChanged() {
        return new PsnIntegrationException(HttpStatus.CONFLICT, LINK_CHANGED);
    }

    public static PsnIntegrationException invalidOnlineId() {
        return new PsnIntegrationException(HttpStatus.BAD_REQUEST, INVALID_ONLINE_ID);
    }

    public static PsnIntegrationException userNotFound() {
        return new PsnIntegrationException(HttpStatus.NOT_FOUND, USER_NOT_FOUND);
    }

    public static PsnIntegrationException rateLimited() {
        return new PsnIntegrationException(HttpStatus.TOO_MANY_REQUESTS, RATE_LIMITED);
    }

    public static PsnIntegrationException unavailable() {
        return new PsnIntegrationException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
    }
}
