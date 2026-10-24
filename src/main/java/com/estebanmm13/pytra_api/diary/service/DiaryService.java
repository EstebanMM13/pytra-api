package com.estebanmm13.pytra_api.diary.service;

import com.estebanmm13.pytra_api.diary.dto.DiaryDayRequestDto;
import com.estebanmm13.pytra_api.diary.dto.DiaryDayResponseDto;
import com.estebanmm13.pytra_api.diary.dto.GameDiaryDayResponseDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DiaryService {
    /** The user's diary days within {@code from..to} (both included), oldest first; empty days are left out. */
    List<DiaryDayResponseDto> findRange(LocalDate from, LocalDate to, Long userId);
    DiaryDayResponseDto findDay(LocalDate date, Long userId);
    /**
     * Replaces the whole day and moves the hours of the game data by the difference. A day with no games and
     * no note is deleted, hence the empty result.
     */
    Optional<DiaryDayResponseDto> saveDay(LocalDate date, DiaryDayRequestDto request, Long userId);
    void deleteDay(LocalDate date, Long userId);
    /** The days one game was played, latest first. */
    List<GameDiaryDayResponseDto> findByGame(Long gameId, Long userId);
}
