package com.estebanmm13.pytra_api.error;

import java.util.Map;

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
    /** The hours assigned to an online playtime's platforms add up to more than its total hours. */
    public static final String PLATFORM_HOURS_EXCEED_TOTAL = "PLATFORM_HOURS_EXCEED_TOTAL";
    public static final String INVALID_COVER_URL = "INVALID_COVER_URL";
    public static final String INVALID_COVER_WIDTH = "INVALID_COVER_WIDTH";
    /** ONLINE games are tracked through online playtime only: new runs are refused. */
    public static final String ONLINE_GAME_RUN = "ONLINE_GAME_RUN";

    /** Diary day dates go from 1970-01-01 to tomorrow (same bounds as run dates). */
    public static final String INVALID_DIARY_DATE = "INVALID_DIARY_DATE";
    /** A diary range needs from <= to and spans at most {@code DiaryServiceImpl.MAX_RANGE_DAYS} days. */
    public static final String INVALID_DIARY_RANGE = "INVALID_DIARY_RANGE";
    /** The same game appears twice in one diary day. */
    public static final String DUPLICATE_DIARY_GAME = "DUPLICATE_DIARY_GAME";
    /**
     * A diary game whose hours must go to a run but which run is ambiguous (none or several in progress):
     * the request has to send {@code experienceId}. fieldErrors names each game as {@code games[i].experienceId}.
     */
    public static final String DIARY_RUN_REQUIRED = "DIARY_RUN_REQUIRED";
    /** The {@code experienceId} sent for a diary game is not one of that game's runs. */
    public static final String INVALID_DIARY_RUN = "INVALID_DIARY_RUN";
    /**
     * A diary game whose hours go to online playtime played online on two or more platforms, sent without
     * {@code platformId}. fieldErrors names each game as {@code games[i].platformId}.
     */
    public static final String DIARY_PLATFORM_REQUIRED = "DIARY_PLATFORM_REQUIRED";

    private final Map<String, String> fieldErrors;

    public InvalidRequestException(String code) {
        this(code, Map.of());
    }

    /** Same code plus per-field details (field path -> code) for the client to point at. */
    public InvalidRequestException(String code, Map<String, String> fieldErrors) {
        super(code);
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
