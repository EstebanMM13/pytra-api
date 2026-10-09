package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.stats.dto.*;

import java.util.List;

public interface StatsService {
    StatsSummaryDto getSummary(Long userId);
    List<YearStatDto> getByYear(Long userId);
    List<SagaStatDto> getBySaga(Long userId);
    List<GenreStatDto> getByGenre(Long userId);
    List<TopRatedExperienceDto> getTopRated(Long userId, int limit);
    List<MostPlayedGameDto> getMostPlayedSingleplayer(Long userId, int limit);
    List<MostPlayedGameDto> getMostPlayedOnline(Long userId, int limit);
}
