package com.estebanmm13.pytra_api.error;

/** A well-formed request the API refuses (400); the message is a stable code clients can rely on. */
public class InvalidRequestException extends RuntimeException {

    public static final String INVALID_YEAR = "INVALID_YEAR";
    public static final String INVALID_EXPORT_FORMAT = "INVALID_EXPORT_FORMAT";
    public static final String CONFIRMATION_MISMATCH = "CONFIRMATION_MISMATCH";
    public static final String INVALID_PASSWORD = "INVALID_PASSWORD";
    public static final String INVALID_AVATAR = "INVALID_AVATAR";
    public static final String INVALID_SEARCH_QUERY = "INVALID_SEARCH_QUERY";
    public static final String INVALID_STEAM_APP_ID = "INVALID_STEAM_APP_ID";
    public static final String INVALID_IGDB_ID = "INVALID_IGDB_ID";
    public static final String INVALID_PLATFORM = "INVALID_PLATFORM";
    public static final String INVALID_COVER_URL = "INVALID_COVER_URL";
    public static final String INVALID_COVER_WIDTH = "INVALID_COVER_WIDTH";
    /** ONLINE games are tracked through online playtime only: new runs are refused. */
    public static final String ONLINE_GAME_RUN = "ONLINE_GAME_RUN";

    public InvalidRequestException(String code) {
        super(code);
    }
}
