package com.estebanmm13.pytra_api.steamsync.service;

import com.estebanmm13.pytra_api.steamsync.dto.SteamStatusDto;
import com.estebanmm13.pytra_api.steamsync.model.SteamLink;

public interface SteamLinkService {
    String createLinkState(Long userId);
    Long consumeLinkState(String rawToken);
    SteamLink upsertLink(Long userId, String steamId64, String personaName);
    SteamStatusDto getStatus(Long userId);
    void unlink(Long userId);
}
