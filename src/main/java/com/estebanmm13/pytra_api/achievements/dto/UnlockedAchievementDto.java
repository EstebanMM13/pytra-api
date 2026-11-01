package com.estebanmm13.pytra_api.achievements.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** One unlocked achievement: the web's id and when it was first unlocked. */
@Getter
@AllArgsConstructor
public class UnlockedAchievementDto {
    private String id;
    private LocalDateTime unlockedAt;
}
