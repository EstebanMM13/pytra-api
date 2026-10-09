package com.estebanmm13.pytra_api.steamsync.service;

import com.estebanmm13.pytra_api.games.dto.game.GameRequestDto;
import com.estebanmm13.pytra_api.games.dto.game.GameResponseDto;
import com.estebanmm13.pytra_api.steamsync.dto.SteamIgnoredAppDto;
import com.estebanmm13.pytra_api.steamsync.dto.SteamSyncResultDto;

import java.util.List;

public interface SteamSyncService {
    SteamSyncResultDto sync(Long userId);
    List<GameResponseDto> getPending(Long userId);
    GameResponseDto confirmPending(Long gameId, GameRequestDto gameRequestDto, Long userId);
    void ignorePending(Long gameId, Long userId);
    List<SteamIgnoredAppDto> getIgnored(Long userId);
    void unignore(String appId, Long userId);
}
