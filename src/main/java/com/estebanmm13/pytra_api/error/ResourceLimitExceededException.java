package com.estebanmm13.pytra_api.error;

/** A global quota is exhausted (409); the message is a stable code clients can rely on. */
public class ResourceLimitExceededException extends RuntimeException {

    public static final String PLATFORM_CATALOG_FULL = "PLATFORM_CATALOG_FULL";
    /** The user already has the maximum number of personal access tokens. */
    public static final String ACCESS_TOKEN_LIMIT = "ACCESS_TOKEN_LIMIT";

    public ResourceLimitExceededException(String code) {
        super(code);
    }
}
