package com.estebanmm13.pytra_api.games.dto.game;

import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.games.dto.genre.GenreResponseDto;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.platforms.dto.PlatformRefDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class GameResponseDto {
    private Long id;
    private String name;
    private String developer;
    private String publisher;
    private LocalDate releaseDate;
    private GameCategory category;
    private Long sagaId;
    private String sagaName;
    private String coverImageUrl;
    private ReviewStatus reviewStatus;
    private List<GenreResponseDto> genres;
    private LocalDateTime updatedAt;

    // Library aggregates over the game's experiences. Filled by the /games endpoints;
    // null on responses that don't compute them (Steam pending/confirm).
    private Long experienceCount;
    private Double totalHours;
    private BigDecimal bestRating;
    private ExperienceStatus lastExperienceStatus;
    private Integer lastPlayedYear;
    private Boolean hasPlatinum;
    /**
     * Legacy enum codes of the game's platforms, distinct, in catalog order ("OTHER" for platforms the
     * old enum did not have). Kept for clients built before the catalog; new clients use platformRefs.
     */
    private List<String> platforms;
    /** Distinct catalog platforms of the game's experiences, in catalog order. */
    private List<PlatformRefDto> platformRefs;
    /** Latest endDate/startDate among the game's experiences; null when none has a date. */
    private LocalDate lastPlayedAt;
}
