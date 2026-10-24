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
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OnlinePlaytimeServiceImpl implements OnlinePlaytimeService {

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
        if (onlinePlaytimeRequestDto.getPlatformIds() != null) {
            // Absent field = keep: clients built before online platforms existed never wipe them.
            onlinePlaytime.setPlatforms(resolvePlatforms(onlinePlaytimeRequestDto.getPlatformIds()));
        }

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
