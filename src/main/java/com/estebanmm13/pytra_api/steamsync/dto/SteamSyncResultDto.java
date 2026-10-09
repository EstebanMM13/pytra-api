package com.estebanmm13.pytra_api.steamsync.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SteamSyncResultDto {
    private int gamesScanned;
    private int newGamesPending;
    private int gamesUpdated;
}
