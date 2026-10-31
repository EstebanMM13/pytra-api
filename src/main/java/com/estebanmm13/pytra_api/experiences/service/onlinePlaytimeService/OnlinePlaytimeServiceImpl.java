package com.estebanmm13.pytra_api.experiences.service.onlinePlaytimeService;

import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.experiences.dto.onlinePlaytime.OnlinePlaytimeRequestDto;
import com.estebanmm13.pytra_api.experiences.dto.onlinePlaytime.OnlinePlaytimeResponseDto;
import com.estebanmm13.pytra_api.experiences.mapper.OnlinePlaytimeMapper;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.experiences.service.experienceService.HoursArithmetic;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.platforms.model.Platform;
import com.estebanmm13.pytra_api.platforms.repository.PlatformRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OnlinePlaytimeServiceImpl implements OnlinePlaytimeService {

    private static final double HOURS_TOLERANCE = 1e-6;

    private final OnlinePlaytimeRepository onlinePlaytimeRepository;
    private final GameRepository gameRepository;
    private final OnlinePlaytimeMapper onlinePlaytimeMapper;
    private final PlatformRepository platformRepository;

    @Override
    public OnlinePlaytimeResponseDto findByGame(Long gameId, Long userId) {
        requireOwnedGame(gameId, userId);

        OnlinePlaytime onlinePlaytime = onlinePlaytimeRepository.findByGameIdAndUserId(gameId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Online playtime not found"));
        return onlinePlaytimeMapper.toResponseDto(onlinePlaytime);
    }

    @Override
    @Transactional
    public OnlinePlaytimeResponseDto upsert(Long gameId, OnlinePlaytimeRequestDto onlinePlaytimeRequestDto, Long userId) {
        requireOwnedGame(gameId, userId);

        Optional<OnlinePlaytime> existing = onlinePlaytimeRepository.findByGameIdAndUserId(gameId, userId);

        OnlinePlaytime onlinePlaytime = existing.orElseGet(() ->
                OnlinePlaytime.builder()
                        .userId(userId)
                        .gameId(gameId)
                        .build()
        );

        onlinePlaytime.setTotalHours(onlinePlaytimeRequestDto.getTotalHours());
        onlinePlaytime.setLastSessionAt(onlinePlaytimeRequestDto.getLastSessionAt());
        onlinePlaytime.setGeneralRating(onlinePlaytimeRequestDto.getGeneralRating());
        onlinePlaytime.setNotes(onlinePlaytimeRequestDto.getNotes());
        // Absent flag = keep, so an old client saving hours never clears a platinum or 100%.
        if (onlinePlaytimeRequestDto.getPlatinum() != null) {
            onlinePlaytime.setPlatinum(onlinePlaytimeRequestDto.getPlatinum());
        }
        if (onlinePlaytimeRequestDto.getFullCompletion() != null) {
            onlinePlaytime.setFullCompletion(onlinePlaytimeRequestDto.getFullCompletion());
        }
        if (onlinePlaytimeRequestDto.getPlatformIds() != null) {
            // Absent field = keep: clients built before online platforms existed never wipe them.
            onlinePlaytime.setPlatforms(resolvePlatforms(onlinePlaytimeRequestDto.getPlatformIds()));
        }
        applyPlatformHours(onlinePlaytime, onlinePlaytimeRequestDto.getPlatformHours());

        onlinePlaytimeRepository.save(onlinePlaytime);
        return onlinePlaytimeMapper.toResponseDto(onlinePlaytime);
    }

    @Override
    @Transactional
    public void addHours(Long gameId, BigDecimal deltaHours, Long userId) {
        requireOwnedGame(gameId, userId);

        Optional<OnlinePlaytime> existing = onlinePlaytimeRepository.findByGameIdAndUserId(gameId, userId);
        if (existing.isEmpty() && deltaHours.signum() <= 0) {
            return;
        }
        OnlinePlaytime onlinePlaytime = existing.orElseGet(() ->
                OnlinePlaytime.builder()
                        .userId(userId)
                        .gameId(gameId)
                        .totalHours(0.0)
                        .build()
        );
        onlinePlaytime.setTotalHours(HoursArithmetic.add(onlinePlaytime.getTotalHours(), deltaHours));
        onlinePlaytimeRepository.save(onlinePlaytime);
    }

    /**
     * Sets the per-platform breakdown (null keeps the stored one), drops the hours of platforms no longer
     * played online and clears it below two platforms. Validated against the record's platforms and total.
     */
    private static void applyPlatformHours(OnlinePlaytime onlinePlaytime, Map<Long, Double> requested) {
        Set<Long> platformIds = onlinePlaytime.getPlatforms().stream().map(Platform::getId).collect(Collectors.toSet());
        if (requested != null) {
            if (!platformIds.containsAll(requested.keySet())) {
                throw new InvalidRequestException(InvalidRequestException.INVALID_PLATFORM);
            }
            double assigned = requested.values().stream().mapToDouble(Double::doubleValue).sum();
            // Tolerance for decimal sums like 0.1 + 0.2.
            if (assigned - onlinePlaytime.getTotalHours() > HOURS_TOLERANCE) {
                throw new InvalidRequestException(InvalidRequestException.PLATFORM_HOURS_EXCEED_TOTAL);
            }
            onlinePlaytime.getPlatformHours().clear();
            onlinePlaytime.getPlatformHours().putAll(requested);
        }
        if (platformIds.size() < 2) {
            onlinePlaytime.getPlatformHours().clear();
        } else {
            onlinePlaytime.getPlatformHours().keySet().retainAll(platformIds);
        }
    }

    /** Catalog entries for the ids; any unknown id rejects the whole request. */
    private Set<Platform> resolvePlatforms(Set<Long> platformIds) {
        if (platformIds.isEmpty()) {
            return new HashSet<>();
        }
        List<Platform> found = platformRepository.findAllById(platformIds);
        if (found.size() != platformIds.size()) {
            throw new InvalidRequestException(InvalidRequestException.INVALID_PLATFORM);
        }
        return new HashSet<>(found);
    }

    private void requireOwnedGame(Long gameId, Long userId) {
        if (!gameRepository.findByIdAndUserId(gameId, userId).isPresent()) {
            throw new ResourceNotFoundException("Game not found");
        }
    }
}
