package com.estebanmm13.pytra_api.psnsync.service;

import com.estebanmm13.pytra_api.psnsync.dto.PsnStatusDto;

public interface PsnLinkService {
    PsnStatusDto getStatus(Long userId);
    PsnStatusDto link(Long userId, String onlineId);
    void unlink(Long userId);
}
