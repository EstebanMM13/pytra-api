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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    @Test
    void addHoursCreatesTheMissingRowForAPositiveChange() {
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.empty());

        service.addHours(GAME_ID, new java.math.BigDecimal("1.5"), USER_ID);

        org.mockito.ArgumentCaptor<OnlinePlaytime> captor = org.mockito.ArgumentCaptor.forClass(OnlinePlaytime.class);
        verify(onlinePlaytimeRepository).save(captor.capture());
        assertThat(captor.getValue().getTotalHours()).isEqualTo(1.5);
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getGameId()).isEqualTo(GAME_ID);
    }

    @Test
    void addHoursWithoutARowIgnoresASubtraction() {
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.empty());

        service.addHours(GAME_ID, new java.math.BigDecimal("-1.5"), USER_ID);

        verify(onlinePlaytimeRepository, never()).save(any());
    }

    @Test
    void addHoursChangesTheTotalAndNeverGoesBelowZero() {
        OnlinePlaytime existing = OnlinePlaytime.builder().id(1L).userId(USER_ID).gameId(GAME_ID).totalHours(10.1).build();
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(existing));

        service.addHours(GAME_ID, new java.math.BigDecimal("0.2"), USER_ID);
        assertThat(existing.getTotalHours()).isEqualTo(10.3);

        service.addHours(GAME_ID, new java.math.BigDecimal("-20"), USER_ID);
        assertThat(existing.getTotalHours()).isEqualTo(0.0);
    }

    @Test
    void newRowWithoutFlagsHasThemFalse() {
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.empty());

        OnlinePlaytimeResponseDto response = service.upsert(GAME_ID, request(null), USER_ID);

        assertThat(response.getPlatinum()).isFalse();
        assertThat(response.getFullCompletion()).isFalse();
    }

    @Test
    void flagsAreStoredIndependentlyAndMissingFlagsKeepTheStoredOnes() {
        OnlinePlaytime existing = OnlinePlaytime.builder()
                .id(1L).userId(USER_ID).gameId(GAME_ID).totalHours(5.0).platinum(true).build();
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(existing));

        OnlinePlaytimeRequestDto oldClient = request(null);
        OnlinePlaytimeResponseDto kept = service.upsert(GAME_ID, oldClient, USER_ID);
        assertThat(kept.getPlatinum()).isTrue();
        assertThat(kept.getFullCompletion()).isFalse();

        OnlinePlaytimeRequestDto toggled = request(null);
        toggled.setPlatinum(false);
        toggled.setFullCompletion(true);
        OnlinePlaytimeResponseDto response = service.upsert(GAME_ID, toggled, USER_ID);
        assertThat(response.getPlatinum()).isFalse();
        assertThat(response.getFullCompletion()).isTrue();
        assertThat(existing.getFullCompletion()).isTrue();
    }

    @Test
    void addHoursNeverTouchesTheFlags() {
        OnlinePlaytime existing = OnlinePlaytime.builder()
                .id(1L).userId(USER_ID).gameId(GAME_ID).totalHours(5.0).fullCompletion(true).build();
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(existing));

        service.addHours(GAME_ID, new java.math.BigDecimal("2"), USER_ID);

        assertThat(existing.getFullCompletion()).isTrue();
        assertThat(existing.getPlatinum()).isFalse();
    }

    @Test
    void platformHoursAreStoredAndReturned() {
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.empty());
        Set<Long> ids = Set.of(TestPlatforms.PC.getId(), TestPlatforms.PS5.getId());
        when(platformRepository.findAllById(ids)).thenReturn(List.of(TestPlatforms.PC, TestPlatforms.PS5));
        OnlinePlaytimeRequestDto dto = request(ids);
        dto.setPlatformHours(Map.of(TestPlatforms.PC.getId(), 4.0, TestPlatforms.PS5.getId(), 6.5));

        OnlinePlaytimeResponseDto response = service.upsert(GAME_ID, dto, USER_ID);

        assertThat(response.getPlatformHours())
                .containsExactlyInAnyOrderEntriesOf(Map.of(TestPlatforms.PC.getId(), 4.0, TestPlatforms.PS5.getId(), 6.5));
        verify(onlinePlaytimeRepository).save(any());
    }

    @Test
    void platformHoursForAPlatformNotPlayedOnlineAreRejected() {
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.empty());
        Set<Long> ids = Set.of(TestPlatforms.PC.getId(), TestPlatforms.PS5.getId());
        when(platformRepository.findAllById(ids)).thenReturn(List.of(TestPlatforms.PC, TestPlatforms.PS5));
        OnlinePlaytimeRequestDto dto = request(ids);
        dto.setPlatformHours(Map.of(TestPlatforms.SWITCH.getId(), 1.0));

        assertThatThrownBy(() -> service.upsert(GAME_ID, dto, USER_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage(InvalidRequestException.INVALID_PLATFORM);
        verify(onlinePlaytimeRepository, never()).save(any());
    }

    @Test
    void platformHoursAboveTheTotalAreRejected() {
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.empty());
        Set<Long> ids = Set.of(TestPlatforms.PC.getId(), TestPlatforms.PS5.getId());
        when(platformRepository.findAllById(ids)).thenReturn(List.of(TestPlatforms.PC, TestPlatforms.PS5));
        OnlinePlaytimeRequestDto dto = request(ids);
        dto.setPlatformHours(Map.of(TestPlatforms.PC.getId(), 10.0, TestPlatforms.PS5.getId(), 2.5));

        assertThatThrownBy(() -> service.upsert(GAME_ID, dto, USER_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage(InvalidRequestException.PLATFORM_HOURS_EXCEED_TOTAL);
        verify(onlinePlaytimeRepository, never()).save(any());
    }

    @Test
    void missingPlatformHoursKeepTheStoredOnesAndDropRemovedPlatforms() {
        OnlinePlaytime existing = OnlinePlaytime.builder()
                .id(1L).userId(USER_ID).gameId(GAME_ID).totalHours(12.0)
                .platforms(new HashSet<>(Set.of(TestPlatforms.PC, TestPlatforms.PS5, TestPlatforms.SWITCH)))
                .platformHours(new HashMap<>(Map.of(TestPlatforms.PC.getId(), 3.0, TestPlatforms.SWITCH.getId(), 2.0)))
                .build();
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(existing));
        Set<Long> ids = Set.of(TestPlatforms.PC.getId(), TestPlatforms.PS5.getId());
        when(platformRepository.findAllById(ids)).thenReturn(List.of(TestPlatforms.PC, TestPlatforms.PS5));

        OnlinePlaytimeResponseDto response = service.upsert(GAME_ID, request(ids), USER_ID);

        assertThat(response.getPlatformHours()).containsExactlyEntriesOf(Map.of(TestPlatforms.PC.getId(), 3.0));
    }

    @Test
    void platformHoursAreClearedWithFewerThanTwoPlatforms() {
        OnlinePlaytime existing = OnlinePlaytime.builder()
                .id(1L).userId(USER_ID).gameId(GAME_ID).totalHours(12.0)
                .platforms(new HashSet<>(Set.of(TestPlatforms.PC, TestPlatforms.PS5)))
                .platformHours(new HashMap<>(Map.of(TestPlatforms.PC.getId(), 3.0)))
                .build();
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(existing));
        Set<Long> ids = Set.of(TestPlatforms.PC.getId());
        when(platformRepository.findAllById(ids)).thenReturn(List.of(TestPlatforms.PC));

        OnlinePlaytimeResponseDto response = service.upsert(GAME_ID, request(ids), USER_ID);

        assertThat(response.getPlatformHours()).isEmpty();
    }

    @Test
    void addHoursOnAPlatformAlsoMovesItsShareOfTheBreakdown() {
        OnlinePlaytime existing = OnlinePlaytime.builder()
                .id(1L).userId(USER_ID).gameId(GAME_ID).totalHours(10.0)
                .platforms(new HashSet<>(Set.of(TestPlatforms.PC, TestPlatforms.PS5)))
                .platformHours(new HashMap<>(Map.of(TestPlatforms.PC.getId(), 4.0)))
                .build();
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(existing));

        service.addHours(GAME_ID, new java.math.BigDecimal("2.5"), TestPlatforms.PS5.getId(), USER_ID);
        service.addHours(GAME_ID, new java.math.BigDecimal("-6"), TestPlatforms.PC.getId(), USER_ID);

        assertThat(existing.getTotalHours()).isEqualTo(6.5);
        // Never below 0: PC had 4 h, so taking 6 leaves it without hours.
        assertThat(existing.getPlatformHours()).containsExactlyEntriesOf(Map.of(TestPlatforms.PS5.getId(), 2.5));
    }

    @Test
    void addHoursOnAPlatformOnlyChangesTheTotalBelowTwoPlatforms() {
        OnlinePlaytime existing = OnlinePlaytime.builder()
                .id(1L).userId(USER_ID).gameId(GAME_ID).totalHours(10.0)
                .platforms(new HashSet<>(Set.of(TestPlatforms.PC)))
                .build();
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(existing));

        service.addHours(GAME_ID, new java.math.BigDecimal("2"), TestPlatforms.PC.getId(), USER_ID);

        assertThat(existing.getTotalHours()).isEqualTo(12.0);
        assertThat(existing.getPlatformHours()).isEmpty();
    }

    @Test
    void platformIdsOfListsTheOnlinePlatformsOrNoneWithoutARecord() {
        OnlinePlaytime existing = OnlinePlaytime.builder()
                .id(1L).userId(USER_ID).gameId(GAME_ID).totalHours(10.0)
                .platforms(new HashSet<>(Set.of(TestPlatforms.PC, TestPlatforms.PS5)))
                .build();
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(existing));
        assertThat(service.platformIdsOf(GAME_ID, USER_ID))
                .containsExactlyInAnyOrder(TestPlatforms.PC.getId(), TestPlatforms.PS5.getId());

        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.empty());
        assertThat(service.platformIdsOf(GAME_ID, USER_ID)).isEmpty();
    }

    private static OnlinePlaytimeRequestDto request(Set<Long> platformIds) {
        OnlinePlaytimeRequestDto dto = new OnlinePlaytimeRequestDto();
        dto.setTotalHours(12.0);
        dto.setPlatformIds(platformIds);
        return dto;
    }
}
