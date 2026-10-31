package com.estebanmm13.pytra_api.experiences.service.onlinePlaytimeService;

import com.estebanmm13.pytra_api.experiences.dto.onlinePlaytime.OnlinePlaytimeRequestDto;
import com.estebanmm13.pytra_api.experiences.dto.onlinePlaytime.OnlinePlaytimeResponseDto;

import java.math.BigDecimal;
import java.util.Set;

public interface OnlinePlaytimeService {
    OnlinePlaytimeResponseDto findByGame(Long gameId, Long userId);
    OnlinePlaytimeResponseDto upsert(Long gameId, OnlinePlaytimeRequestDto onlinePlaytimeRequestDto, Long userId);
    /**
     * Adds {@code deltaHours} (negative subtracts) to the game's online total, never going below 0. A missing
     * row is created for a positive change. Used by the play diary.
     */
    void addHours(Long gameId, BigDecimal deltaHours, Long userId);

    /**
     * Like {@link #addHours(Long, BigDecimal, Long)}, and also moves the hours of {@code platformId} in the
     * per-platform breakdown (never below 0) when it is one of two or more online platforms.
     */
    void addHours(Long gameId, BigDecimal deltaHours, Long platformId, Long userId);

    /** Ids of the platforms the game is played online on; empty without an online playtime record. */
    Set<Long> platformIdsOf(Long gameId, Long userId);
}
