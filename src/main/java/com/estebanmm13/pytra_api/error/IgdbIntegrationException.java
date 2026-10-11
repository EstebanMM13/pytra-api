package com.estebanmm13.pytra_api.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Failure of the IGDB metadata lookup with a stable {@link #getCode() code} clients can rely on.
 * The message never contains upstream details (the Twitch token request carries the client secret).
 */
@Getter
public class IgdbIntegrationException extends RuntimeException {

    public static final String DISABLED = "IGDB_DISABLED";
    public static final String GAME_NOT_FOUND = "IGDB_GAME_NOT_FOUND";
    public static final String UNAVAILABLE = "IGDB_UNAVAILABLE";

    private final HttpStatus status;
    private final String code;

    public IgdbIntegrationException(HttpStatus status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }

    /** No Twitch credentials configured: the feature does not exist on this deployment. */
    public static IgdbIntegrationException disabled() {
        return new IgdbIntegrationException(HttpStatus.NOT_FOUND, DISABLED);
    }

    public static IgdbIntegrationException gameNotFound() {
        return new IgdbIntegrationException(HttpStatus.NOT_FOUND, GAME_NOT_FOUND);
    }

    /** Timeouts, HTTP errors, rate limiting, rejected credentials, unparseable bodies. */
    public static IgdbIntegrationException unavailable() {
        return new IgdbIntegrationException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
    }
}
