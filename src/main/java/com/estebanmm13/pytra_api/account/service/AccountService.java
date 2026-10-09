package com.estebanmm13.pytra_api.account.service;

import com.estebanmm13.pytra_api.account.export.ExportFile;

public interface AccountService {

    /** @param format {@code csv} or {@code markdown}/{@code md} */
    ExportFile export(Long userId, String format);

    /** Deletes the user and everything they own; {@code confirm} must equal their username. */
    void deleteAccount(Long userId, String confirm);
}
