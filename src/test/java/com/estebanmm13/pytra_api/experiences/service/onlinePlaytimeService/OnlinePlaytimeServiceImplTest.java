package com.estebanmm13.pytra_api.experiences.service.onlinePlaytimeService;

import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.experiences.dto.onlinePlaytime.OnlinePlaytimeRequestDto;
import com.estebanmm13.pytra_api.experiences.dto.onlinePlaytime.OnlinePlaytimeResponseDto;
import com.estebanmm13.pytra_api.experiences.mapper.OnlinePlaytimeMapper;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.platforms.TestPlatforms;
import com.estebanmm13.pytra_api.platforms.dto.PlatformRefDto;
import com.estebanmm13.pytra_api.platforms.repository.PlatformRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnlinePlaytimeServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long GAME_ID = 3L;

    private final OnlinePlaytimeRepository onlinePlaytimeRepository = mock(OnlinePlaytimeRepository.class);
    private final GameRepository gameRepository = mock(GameRepository.class);
    private final PlatformRepository platformRepository = mock(PlatformRepository.class);
    private final OnlinePlaytimeServiceImpl service = new OnlinePlaytimeServiceImpl(
            onlinePlaytimeRepository, gameRepository, new OnlinePlaytimeMapper(), platformRepository);

    @BeforeEach
    void ownedGame() {
        when(gameRepository.findByIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(new Game()));
        when(onlinePlaytimeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void platformsAreStoredAndReturnedInCatalogOrder() {
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.empty());
        Set<Long> ids = new LinkedHashSet<>(List.of(TestPlatforms.SWITCH.getId(), TestPlatforms.PC.getId()));
        when(platformRepository.findAllById(ids)).thenReturn(List.of(TestPlatforms.SWITCH, TestPlatforms.PC));

        OnlinePlaytimeResponseDto response = service.upsert(GAME_ID, request(ids), USER_ID);

        assertThat(response.getPlatforms()).extracting(PlatformRefDto::name).containsExactly("PC", "Switch");
    }

    @Test
    void unknownPlatformIdIsRejected() {
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.empty());
        Set<Long> ids = Set.of(TestPlatforms.PC.getId(), 999L);
        when(platformRepository.findAllById(ids)).thenReturn(List.of(TestPlatforms.PC));

        assertThatThrownBy(() -> service.upsert(GAME_ID, request(ids), USER_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage(InvalidRequestException.INVALID_PLATFORM);
        verify(onlinePlaytimeRepository, never()).save(any());
    }

    @Test
    void missingPlatformIdsKeepTheStoredPlatforms() {
        OnlinePlaytime existing = OnlinePlaytime.builder()
                .id(1L).userId(USER_ID).gameId(GAME_ID).totalHours(5.0)
                .platforms(new HashSet<>(Set.of(TestPlatforms.PS5)))
                .build();
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(existing));

        OnlinePlaytimeResponseDto response = service.upsert(GAME_ID, request(null), USER_ID);

        assertThat(response.getPlatforms()).extracting(PlatformRefDto::name).containsExactly("PS5");
        verify(platformRepository, never()).findAllById(any());
    }

    @Test
    void emptyPlatformIdsClearTheStoredPlatforms() {
        OnlinePlaytime existing = OnlinePlaytime.builder()
                .id(1L).userId(USER_ID).gameId(GAME_ID).totalHours(5.0)
                .platforms(new HashSet<>(Set.of(TestPlatforms.PS5)))
                .build();
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(existing));

        OnlinePlaytimeResponseDto response = service.upsert(GAME_ID, request(Set.of()), USER_ID);

        assertThat(response.getPlatforms()).isEmpty();
    }

    private static OnlinePlaytimeRequestDto request(Set<Long> platformIds) {
        OnlinePlaytimeRequestDto dto = new OnlinePlaytimeRequestDto();
        dto.setTotalHours(12.0);
        dto.setPlatformIds(platformIds);
        return dto;
    }
}
