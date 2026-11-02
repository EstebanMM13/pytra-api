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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AndroidLinkServiceTest {

    private static final Long USER_ID = 5L;
    private static final Long GAME_ID = 42L;
    private static final String PACKAGE = "com.supercell.clashroyale";
    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");

    private final GamePlatformLinkRepository linkRepository = mock(GamePlatformLinkRepository.class);
    private final GameRepository gameRepository = mock(GameRepository.class);
    private final AndroidLinkService service =
            new AndroidLinkService(linkRepository, gameRepository, Clock.fixed(NOW, ZoneId.of("UTC")));
    private final Game game = Game.builder().id(GAME_ID).userId(USER_ID).name("Clash Royale").build();

    @BeforeEach
    void setUp() {
        when(gameRepository.findByIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(game));
        when(linkRepository.findByUserIdAndPlatformAndExternalId(any(), any(), any())).thenReturn(Optional.empty());
        when(linkRepository.findByUserIdAndGameIdAndPlatform(any(), any(), any())).thenReturn(Optional.empty());
        when(linkRepository.save(any(GamePlatformLink.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createLinksThePackageFromNowOnWithNoSyncedMinutes() {
        AndroidLinkResponseDto created = service.create(USER_ID, new AndroidLinkRequestDto(PACKAGE, GAME_ID));

        assertThat(created).isEqualTo(new AndroidLinkResponseDto(PACKAGE, GAME_ID));
        ArgumentCaptor<GamePlatformLink> saved = ArgumentCaptor.forClass(GamePlatformLink.class);
        verify(linkRepository).save(saved.capture());
        GamePlatformLink link = saved.getValue();
        assertThat(link.getUserId()).isEqualTo(USER_ID);
        assertThat(link.getGame()).isSameAs(game);
        assertThat(link.getPlatform()).isEqualTo(ExternalPlatform.ANDROID);
        assertThat(link.getExternalId()).isEqualTo(PACKAGE);
        assertThat(link.getLastSyncedPlaytimeMinutes()).isZero();
        assertThat(link.getLinkedAt()).isEqualTo(LocalDateTime.of(2026, 11, 2, 9, 0));
        assertThat(link.getLastSyncedAt()).isEqualTo(link.getLinkedAt());
    }

    @Test
    void createRejectsAGameOfAnotherUser() {
        when(gameRepository.findByIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(USER_ID, new AndroidLinkRequestDto(PACKAGE, GAME_ID)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(linkRepository, never()).save(any());
    }

    @Test
    void createRejectsAPackageAlreadyLinked() {
        when(linkRepository.findByUserIdAndPlatformAndExternalId(USER_ID, ExternalPlatform.ANDROID, PACKAGE))
                .thenReturn(Optional.of(GamePlatformLink.builder().build()));

        assertThatThrownBy(() -> service.create(USER_ID, new AndroidLinkRequestDto(PACKAGE, GAME_ID)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(linkRepository, never()).save(any());
    }

    @Test
    void createRejectsAGameAlreadyLinkedToAnotherApp() {
        when(linkRepository.findByUserIdAndGameIdAndPlatform(USER_ID, GAME_ID, ExternalPlatform.ANDROID))
                .thenReturn(Optional.of(GamePlatformLink.builder().build()));

        assertThatThrownBy(() -> service.create(USER_ID, new AndroidLinkRequestDto(PACKAGE, GAME_ID)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(linkRepository, never()).save(any());
    }

    @Test
    void findAllListsTheAndroidLinksByPackage() {
        Game other = Game.builder().id(7L).userId(USER_ID).name("Minecraft").build();
        when(linkRepository.findAllByUserIdAndPlatformWithGame(USER_ID, ExternalPlatform.ANDROID)).thenReturn(List.of(
                GamePlatformLink.builder().game(game).externalId(PACKAGE).build(),
                GamePlatformLink.builder().game(other).externalId("com.mojang.minecraftpe").build()));

        assertThat(service.findAll(USER_ID)).containsExactly(
                new AndroidLinkResponseDto("com.mojang.minecraftpe", 7L),
                new AndroidLinkResponseDto(PACKAGE, GAME_ID));
    }

    @Test
    void deleteRemovesTheUsersLinkOrFails() {
        GamePlatformLink link = GamePlatformLink.builder().game(game).externalId(PACKAGE).build();
        when(linkRepository.findByUserIdAndPlatformAndExternalId(USER_ID, ExternalPlatform.ANDROID, PACKAGE))
                .thenReturn(Optional.of(link));

        service.delete(USER_ID, PACKAGE);

        verify(linkRepository).delete(link);
        assertThatThrownBy(() -> service.delete(USER_ID, "com.other.app")).isInstanceOf(ResourceNotFoundException.class);
    }
}
