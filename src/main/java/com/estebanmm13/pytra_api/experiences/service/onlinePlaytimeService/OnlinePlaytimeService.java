package com.estebanmm13.pytra_api.experiences.service.onlinePlaytimeService;

import com.estebanmm13.pytra_api.experiences.dto.onlinePlaytime.OnlinePlaytimeRequestDto;
import com.estebanmm13.pytra_api.experiences.dto.onlinePlaytime.OnlinePlaytimeResponseDto;

import java.math.BigDecimal;

public interface OnlinePlaytimeService {
    OnlinePlaytimeResponseDto findByGame(Long gameId, Long userId);
    OnlinePlaytimeResponseDto upsert(Long gameId, OnlinePlaytimeRequestDto onlinePlaytimeRequestDto, Long userId);
    /**
     * Adds {@code deltaHours} (negative subtracts) to the game's online total, never going below 0. A missing
     * row is created for a positive change. Used by the play diary.
     */
    void addHours(Long gameId, BigDecimal deltaHours, Long userId);
}
