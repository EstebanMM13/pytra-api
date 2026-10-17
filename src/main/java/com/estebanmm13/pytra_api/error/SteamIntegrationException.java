package com.estebanmm13.pytra_api.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Failure of the Steam integration with a stable {@link #getCode() code} clients can rely on.
 * The message never contains upstream details (Steam URLs carry the API key in the query string).
 */
@Getter
public class SteamIntegrationException extends RuntimeException {

    public static final String NOT_CONFIGURED = "STEAM_NOT_CONFIGURED";
    public static final String NOT_LINKED = "STEAM_NOT_LINKED";
    public static final String SYNC_IN_PROGRESS = "SYNC_IN_PROGRESS";
    public static final String LINK_CHANGED = "STEAM_LINK_CHANGED";
    public static final String API_KEY_REJECTED = "STEAM_API_KEY_REJECTED";
    public static final String RATE_LIMITED = "STEAM_RATE_LIMITED";
    public static final String UNAVAILABLE = "STEAM_UNAVAILABLE";
    public static final String ACCOUNT_ALREADY_LINKED = "STEAM_ACCOUNT_ALREADY_LINKED";
    public static final String APP_NOT_FOUND = "STEAM_APP_NOT_FOUND";
    public static final String APP_NOT_A_GAME = "STEAM_APP_NOT_A_GAME";
    /** Used as a {@link ResourceNotFoundException} message by the cover thumbnail proxy. */
    public static final String COVER_NOT_FOUND = "COVER_NOT_FOUND";
    public static final String COVER_UNAVAILABLE = "COVER_UNAVAILABLE";

    private final HttpStatus status;
    private final String code;

    public SteamIntegrationException(HttpStatus status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }

    public static SteamIntegrationException notConfigured() {
        return new SteamIntegrationException(HttpStatus.SERVICE_UNAVAILABLE, NOT_CONFIGURED);
    }

    public static SteamIntegrationException notLinked() {
        return new SteamIntegrationException(HttpStatus.NOT_FOUND, NOT_LINKED);
    }

    public static SteamIntegrationException syncInProgress() {
        return new SteamIntegrationException(HttpStatus.CONFLICT, SYNC_IN_PROGRESS);
    }

    public static SteamIntegrationException linkChanged() {
        return new SteamIntegrationException(HttpStatus.CONFLICT, LINK_CHANGED);
    }

    public static SteamIntegrationException apiKeyRejected() {
        return new SteamIntegrationException(HttpStatus.BAD_GATEWAY, API_KEY_REJECTED);
    }

    public static SteamIntegrationException rateLimited() {
        return new SteamIntegrationException(HttpStatus.TOO_MANY_REQUESTS, RATE_LIMITED);
    }

    public static SteamIntegrationException unavailable() {
        return new SteamIntegrationException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
    }

    public static SteamIntegrationException accountAlreadyLinked() {
        return new SteamIntegrationException(HttpStatus.CONFLICT, ACCOUNT_ALREADY_LINKED);
    }

    public static SteamIntegrationException appNotFound() {
        return new SteamIntegrationException(HttpStatus.NOT_FOUND, APP_NOT_FOUND);
    }

    /** The app exists but is a DLC, soundtrack, demo... (appdetails {@code type != "game"}). */
    public static SteamIntegrationException appNotAGame() {
        return new SteamIntegrationException(HttpStatus.UNPROCESSABLE_CONTENT, APP_NOT_A_GAME);
    }

    /** A Steam cover couldn't be fetched or turned into a thumbnail (upstream error, not an image, too big...). */
    public static SteamIntegrationException coverUnavailable() {
        return new SteamIntegrationException(HttpStatus.BAD_GATEWAY, COVER_UNAVAILABLE);
    }
}
