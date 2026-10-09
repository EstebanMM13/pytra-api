package com.estebanmm13.pytra_api.experiences.service.onlinePlaytimeService;

import com.estebanmm13.pytra_api.experiences.dto.onlinePlaytime.OnlinePlaytimeRequestDto;
import com.estebanmm13.pytra_api.experiences.dto.onlinePlaytime.OnlinePlaytimeResponseDto;

public interface OnlinePlaytimeService {
    OnlinePlaytimeResponseDto findByGame(Long gameId, Long userId);
    OnlinePlaytimeResponseDto upsert(Long gameId, OnlinePlaytimeRequestDto onlinePlaytimeRequestDto, Long userId);
}
