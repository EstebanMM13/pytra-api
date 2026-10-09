package com.estebanmm13.pytra_api.steamsync.dto;

import java.time.LocalDateTime;

public record SteamIgnoredAppDto(String appId, String name, LocalDateTime ignoredAt) {
}
