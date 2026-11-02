package com.estebanmm13.pytra_api.androidsync.dto;

/** Outcome of an Android reset: links restarted and minutes taken back from the games. */
public record AndroidUsageResetResponseDto(int links, long minutesRemoved) {
}
