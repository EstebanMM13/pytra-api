package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.Ratings;
import com.estebanmm13.pytra_api.games.repository.GameGenreRow;
import com.estebanmm13.pytra_api.platforms.mapper.PlatformMapper;
import com.estebanmm13.pytra_api.platforms.model.Platform;
import com.estebanmm13.pytra_api.stats.dto.RatingStatsDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static com.estebanmm13.pytra_api.stats.service.InsightMath.requireValidYearIfPresent;

/** Rating profile: histogram, spread and averages by genre and platform. */
final class RatingInsights {

    /** Histogram step: half points, since ratings cluster high and are often given as x.5. */
    private static final BigDecimal RATING_BUCKET_SIZE = new BigDecimal("0.5");
    /** 20 half-point buckets below 10, plus one for perfect 10s. */
    private static final int RATING_BUCKETS = 21;

    private final InsightSources sources;

    RatingInsights(InsightSources sources) {
        this.sources = sources;
    }

    RatingStatsDto ratings(Long userId, Integer year) {
        requireValidYearIfPresent(year);
        List<Experience> rated = sources.runsInScope(userId, year).stream()
                .filter(run -> run.getRating() != null)
                .toList();
        Map<Long, List<GameGenreRow>> genresByGame = sources.confirmedGenresByGame(userId);

        long[] counts = new long[RATING_BUCKETS];
        List<BigDecimal> ratings = new ArrayList<>(rated.size());
        Map<Long, List<BigDecimal>> byGenre = new LinkedHashMap<>();
        Map<Long, String> genreNames = new HashMap<>();
        Map<Long, List<BigDecimal>> byPlatform = new LinkedHashMap<>();
        Map<Long, Platform> platforms = new HashMap<>();
        for (Experience run : rated) {
            BigDecimal rating = run.getRating();
            ratings.add(rating);
            counts[ratingBucketOf(rating)]++;
            for (GameGenreRow genre : genresByGame.getOrDefault(run.getGameId(), List.of())) {
                genreNames.put(genre.genreId(), genre.genreName());
                byGenre.computeIfAbsent(genre.genreId(), id -> new ArrayList<>()).add(rating);
            }
            Platform platform = run.getPlatform();
            platforms.put(platform.getId(), platform);
            byPlatform.computeIfAbsent(platform.getId(), id -> new ArrayList<>()).add(rating);
        }

        List<RatingStatsDto.RatingBucket> histogram = new ArrayList<>(RATING_BUCKETS);
        for (int i = 0; i < RATING_BUCKETS; i++) {
            BigDecimal from = RATING_BUCKET_SIZE.multiply(BigDecimal.valueOf(i));
            BigDecimal to = i < RATING_BUCKETS - 1 ? from.add(RATING_BUCKET_SIZE) : from;
            histogram.add(new RatingStatsDto.RatingBucket(Ratings.normalize(from), Ratings.normalize(to), counts[i]));
        }

        List<RatingStatsDto.GenreRating> genres = new ArrayList<>();
        byGenre.forEach((genreId, values) -> genres.add(new RatingStatsDto.GenreRating(
                genreId, genreNames.get(genreId), values.size(), StatsMath.averageRating(values))));
        genres.sort(Comparator
                .comparing(RatingStatsDto.GenreRating::averageRating, Comparator.reverseOrder())
                .thenComparing(Comparator.comparingLong(RatingStatsDto.GenreRating::ratedCount).reversed())
                .thenComparing(RatingStatsDto.GenreRating::genreName, String.CASE_INSENSITIVE_ORDER));

        List<RatingStatsDto.PlatformRating> platformRatings = new ArrayList<>();
        byPlatform.forEach((platformId, values) -> {
            Platform platform = platforms.get(platformId);
            platformRatings.add(new RatingStatsDto.PlatformRating(platformId, platform.getName(),
                    PlatformMapper.colorOf(platform), values.size(), StatsMath.averageRating(values)));
        });
        platformRatings.sort(Comparator
                .comparing(RatingStatsDto.PlatformRating::averageRating, Comparator.reverseOrder())
                .thenComparing(Comparator.comparingLong(RatingStatsDto.PlatformRating::ratedCount).reversed())
                .thenComparing(RatingStatsDto.PlatformRating::name, String.CASE_INSENSITIVE_ORDER));

        return new RatingStatsDto(year, RATING_BUCKET_SIZE, ratings.size(),
                StatsMath.averageRating(ratings), StatsMath.medianRating(ratings), StatsMath.standardDeviation(ratings),
                histogram, genres, platformRatings);
    }

    /** Half-point bucket index: 9.75 -> 19 ([9.5, 10)), 10 -> 20 (perfect scores). */
    static int ratingBucketOf(BigDecimal rating) {
        int index = rating.multiply(BigDecimal.TWO).setScale(0, RoundingMode.FLOOR).intValue();
        return Math.min(Math.max(index, 0), RATING_BUCKETS - 1);
    }
}
