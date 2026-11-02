package com.estebanmm13.pytra_api.androidsync.service;

import com.estebanmm13.pytra_api.androidsync.dto.AndroidLinkRequestDto;
import com.estebanmm13.pytra_api.androidsync.dto.AndroidLinkResponseDto;
import com.estebanmm13.pytra_api.error.DuplicateResourceException;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Links between installed Android apps and the user's games: {@link GamePlatformLink}s with platform
 * {@link ExternalPlatform#ANDROID} and the package name as external id. One package per game and one game per
 * package. A new link starts at 0 synced minutes; its creation time marks the first day whose play counts.
 * Unlinking keeps the hours already added and the usage baselines (a relinked package never re-imports a day).
 */
@Service
public class AndroidLinkService {

    private final GamePlatformLinkRepository linkRepository;
    private final GameRepository gameRepository;
    private final Clock clock;

    @Autowired
    public AndroidLinkService(GamePlatformLinkRepository linkRepository, GameRepository gameRepository) {
        this(linkRepository, gameRepository, Clock.systemDefaultZone());
    }

    AndroidLinkService(GamePlatformLinkRepository linkRepository, GameRepository gameRepository, Clock clock) {
        this.linkRepository = linkRepository;
        this.gameRepository = gameRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AndroidLinkResponseDto> findAll(Long userId) {
        return linkRepository.findAllByUserIdAndPlatformWithGame(userId, ExternalPlatform.ANDROID).stream()
                .map(AndroidLinkService::toDto)
                .sorted(Comparator.comparing(AndroidLinkResponseDto::packageName))
                .toList();
    }

    @Transactional
    public AndroidLinkResponseDto create(Long userId, AndroidLinkRequestDto request) {
        Game game = gameRepository.findByIdAndUserId(request.gameId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Game not found"));
        if (linkRepository.findByUserIdAndPlatformAndExternalId(userId, ExternalPlatform.ANDROID, request.packageName())
                .isPresent()) {
            throw new DuplicateResourceException("Android app already linked");
        }
        if (linkRepository.findByUserIdAndGameIdAndPlatform(userId, game.getId(), ExternalPlatform.ANDROID).isPresent()) {
            throw new DuplicateResourceException("Game already linked to an Android app");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        GamePlatformLink link = linkRepository.save(GamePlatformLink.builder()
                .userId(userId)
                .game(game)
                .platform(ExternalPlatform.ANDROID)
                .externalId(request.packageName())
                .lastSyncedPlaytimeMinutes(0L)
                .lastSyncedAt(now)
                .linkedAt(now)
                .build());
        return toDto(link);
    }

    @Transactional
    public void delete(Long userId, String packageName) {
        GamePlatformLink link = linkRepository
                .findByUserIdAndPlatformAndExternalId(userId, ExternalPlatform.ANDROID, packageName)
                .orElseThrow(() -> new ResourceNotFoundException("Android link not found"));
        linkRepository.delete(link);
    }

    private static AndroidLinkResponseDto toDto(GamePlatformLink link) {
        return new AndroidLinkResponseDto(link.getExternalId(), link.getGame().getId());
    }
}
