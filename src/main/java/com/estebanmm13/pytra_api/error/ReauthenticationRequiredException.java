package com.estebanmm13.pytra_api.error;

/**
 * A sensitive operation needs a recent login (403, never 401 so clients don't treat it as a dead session).
 */
public class ReauthenticationRequiredException extends RuntimeException {

    public static final String CODE = "REAUTH_REQUIRED";

    public ReauthenticationRequiredException() {
        super(CODE);
    }
}
