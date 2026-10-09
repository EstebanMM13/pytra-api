package com.estebanmm13.pytra_api.account.export;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.stats.model.YearNote;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Everything one user owns that goes into an export, already loaded (games with saga and genres).
 *
 * @param experiencesByGame runs per game id, in the order they should be written
 */
public record ExportData(
        String username,
        LocalDateTime exportedAt,
        List<Game> games,
        Map<Long, List<Experience>> experiencesByGame,
        Map<Long, OnlinePlaytime> onlinePlaytimeByGame,
        List<YearNote> yearNotes) {
}
