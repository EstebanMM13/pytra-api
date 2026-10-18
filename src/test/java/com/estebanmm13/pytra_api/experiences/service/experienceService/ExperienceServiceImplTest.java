package com.estebanmm13.pytra_api.experiences.service.experienceService;

import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.experiences.dto.experience.ExperienceRequestDto;
import com.estebanmm13.pytra_api.experiences.mapper.ExperienceMapper;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.platforms.service.PlatformService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExperienceServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long GAME_ID = 3L;

    private final ExperienceRepository experienceRepository = mock(ExperienceRepository.class);
    private final GameRepository gameRepository = mock(GameRepository.class);
    private final PlatformService platformService = mock(PlatformService.class);
    private final ExperienceServiceImpl service = new ExperienceServiceImpl(
            experienceRepository, gameRepository, mock(ExperienceMapper.class), platformService);

    @Test
    void newRunForAnOnlineGameIsRejected() {
        ownedGame(GameCategory.ONLINE);

        assertThatThrownBy(() -> service.create(GAME_ID, request(), USER_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage(InvalidRequestException.ONLINE_GAME_RUN);
        verify(experienceRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = GameCategory.class, names = {"SINGLEPLAYER", "HYBRID"})
    void newRunForOtherCategoriesIsSaved(GameCategory category) {
        ownedGame(category);

        service.create(GAME_ID, request(), USER_ID);

        verify(experienceRepository).save(any());
    }

    private void ownedGame(GameCategory category) {
        Game game = new Game();
        game.setCategory(category);
        when(gameRepository.findByIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(game));
    }

    private static ExperienceRequestDto request() {
        ExperienceRequestDto request = new ExperienceRequestDto();
        request.setRunLabel("Run 1");
        request.setPlatformId(1L);
        return request;
    }
}
