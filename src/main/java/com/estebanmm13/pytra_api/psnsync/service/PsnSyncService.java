package com.estebanmm13.pytra_api.psnsync.service;

import com.estebanmm13.pytra_api.games.dto.game.GameRequestDto;
import com.estebanmm13.pytra_api.games.dto.game.GameResponseDto;
import com.estebanmm13.pytra_api.psnsync.dto.PsnIgnoredTitleDto;
import com.estebanmm13.pytra_api.psnsync.dto.PsnPendingGameDto;
import com.estebanmm13.pytra_api.psnsync.dto.PsnSyncResultDto;

import java.util.List;

public interface PsnSyncService {
    PsnSyncResultDto sync(Long userId);
    List<PsnPendingGameDto> getPending(Long userId);
    GameResponseDto confirmPending(Long gameId, GameRequestDto gameRequestDto, Long userId);
    void ignorePending(Long gameId, Long userId);
    List<PsnIgnoredTitleDto> getIgnored(Long userId);
    void unignore(String titleId, Long userId);
}
