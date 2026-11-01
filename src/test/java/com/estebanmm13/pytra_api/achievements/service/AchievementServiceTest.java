package com.estebanmm13.pytra_api.achievements.service;

import com.estebanmm13.pytra_api.achievements.dto.UnlockedAchievementDto;
import com.estebanmm13.pytra_api.achievements.model.UnlockedAchievement;
import com.estebanmm13.pytra_api.achievements.repository.UnlockedAchievementRepository;
import com.estebanmm13.pytra_api.error.ResourceLimitExceededException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AchievementServiceTest {

    private static final Long USER_ID = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 10, 30);
    private static final LocalDateTime EARLIER = LocalDateTime.of(2026, 3, 2, 9, 0);

    private final UnlockedAchievementRepository repository = mock(UnlockedAchievementRepository.class);
    private final AchievementService service = new AchievementService(repository,
            Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    private static UnlockedAchievement stored(String id, LocalDateTime at) {
        return UnlockedAchievement.builder().userId(USER_ID).achievementId(id).unlockedAt(at).build();
    }

    @Test
    void listsTheUsersUnlockedAchievementsWithTheirDates() {
        when(repository.findAllByUserIdOrderByUnlockedAtAscAchievementIdAsc(USER_ID))
                .thenReturn(List.of(stored("years10", EARLIER), stored("multitasker", NOW)));

        List<UnlockedAchievementDto> list = service.findUnlocked(USER_ID);

        assertThat(list).extracting(UnlockedAchievementDto::getId).containsExactly("years10", "multitasker");
        assertThat(list).extracting(UnlockedAchievementDto::getUnlockedAt).containsExactly(EARLIER, NOW);
    }

    @Test
    void recordsOnlyNewIdsOnceWithTheCurrentDate() {
        when(repository.findAllByUserIdOrderByUnlockedAtAscAchievementIdAsc(USER_ID))
                .thenReturn(List.of(stored("years10", EARLIER)))
                .thenReturn(List.of(stored("years10", EARLIER), stored("multitasker", NOW)));

        List<UnlockedAchievementDto> list = service.record(USER_ID, List.of("multitasker", "years10", "multitasker"));

        verify(repository, times(1)).insertIfAbsent(USER_ID, "multitasker", NOW);
        verify(repository, never()).insertIfAbsent(USER_ID, "years10", NOW);
        // Already stored ids keep their first date.
        assertThat(list).extracting(UnlockedAchievementDto::getId).containsExactly("years10", "multitasker");
        assertThat(list).extracting(UnlockedAchievementDto::getUnlockedAt).containsExactly(EARLIER, NOW);
    }

    @Test
    void recordingOnlyStoredIdsWritesNothing() {
        when(repository.findAllByUserIdOrderByUnlockedAtAscAchievementIdAsc(USER_ID))
                .thenReturn(List.of(stored("years10", EARLIER)));

        List<UnlockedAchievementDto> list = service.record(USER_ID, List.of("years10"));

        verify(repository, never()).insertIfAbsent(anyLong(), anyString(), any());
        assertThat(list).extracting(UnlockedAchievementDto::getId).containsExactly("years10");
        assertThat(list).extracting(UnlockedAchievementDto::getUnlockedAt).containsExactly(EARLIER);
    }

    @Test
    void refusesToGrowPastTheStoredLimit() {
        List<UnlockedAchievement> full = new ArrayList<>();
        for (int i = 0; i < AchievementService.MAX_STORED_PER_USER; i++) {
            full.add(stored("badge" + i, EARLIER));
        }
        when(repository.findAllByUserIdOrderByUnlockedAtAscAchievementIdAsc(USER_ID)).thenReturn(full);

        assertThatThrownBy(() -> service.record(USER_ID, List.of("oneMore")))
                .isInstanceOf(ResourceLimitExceededException.class)
                .hasMessage(AchievementService.ACHIEVEMENT_LIMIT);
        verify(repository, never()).insertIfAbsent(anyLong(), anyString(), any());
    }
}
