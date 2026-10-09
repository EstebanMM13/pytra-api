package com.estebanmm13.pytra_api.account.service;

import com.estebanmm13.pytra_api.account.export.ExportFile;

import java.time.Instant;

public interface AccountService {

    /** @param format {@code csv} or {@code markdown}/{@code md} */
    ExportFile export(Long userId, String format);

    /**
     * Deletes the user and everything they own. {@code confirm} must equal their username; accounts with a
     * password must also send it, Google-only accounts need a token issued in the last 10 minutes.
     *
     * @param tokenIssuedAt {@code iat} of the caller's JWT (may be null)
     */
    void deleteAccount(Long userId, Instant tokenIssuedAt, String confirm, String password);
}
