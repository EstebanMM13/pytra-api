package com.estebanmm13.pytra_api.error;

/** A global quota is exhausted (409); the message is a stable code clients can rely on. */
public class ResourceLimitExceededException extends RuntimeException {

    public static final String PLATFORM_CATALOG_FULL = "PLATFORM_CATALOG_FULL";

    public ResourceLimitExceededException(String code) {
        super(code);
    }
}
