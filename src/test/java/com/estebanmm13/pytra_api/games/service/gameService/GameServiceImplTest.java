package com.estebanmm13.pytra_api.games.service.gameService;

import com.estebanmm13.pytra_api.experiences.service.gameStats.GameExperienceStatsCalculator;
import com.estebanmm13.pytra_api.games.dto.game.GameRequestDto;
import com.estebanmm13.pytra_api.games.mapper.GameMapper;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.games.repository.GenreRepository;
import com.estebanmm13.pytra_api.games.repository.SagaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The backlog ("Pendiente") flag of a game: set on create, kept on update when the client omits it. */
class GameServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long GAME_ID = 3L;

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final GameServiceImpl service = new GameServiceImpl(gameRepository, mock(SagaRepository.class),
            mock(GenreRepository.class), mock(GameMapper.class), mock(GameExperienceStatsCalculator.class));

    @Test
    void createStoresTheBacklogFlagAndDefaultsItToFalse() {
        service.create(request("Silksong", true), USER_ID);
        service.create(request("Hades", null), USER_ID);

        ArgumentCaptor<Game> saved = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Game::getBacklog).containsExactly(true, false);
    }

    @Test
    void updateSetsTheFlagButKeepsItWhenTheClientDoesNotSendIt() {
        Game game = Game.builder().id(GAME_ID).userId(USER_ID).name("Silksong").category(GameCategory.SINGLEPLAYER).build();
        when(gameRepository.findByIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(game));

        service.update(GAME_ID, request("Silksong", true), USER_ID);
        assertThat(game.getBacklog()).isTrue();

        // Clients built before the flag existed send no value: the backlog stays as it was.
        service.update(GAME_ID, request("Silksong", null), USER_ID);
        assertThat(game.getBacklog()).isTrue();

        service.update(GAME_ID, request("Silksong", false), USER_ID);
        assertThat(game.getBacklog()).isFalse();
    }

    private static GameRequestDto request(String name, Boolean backlog) {
        GameRequestDto request = new GameRequestDto();
        request.setName(name);
        request.setCategory(GameCategory.SINGLEPLAYER);
        request.setBacklog(backlog);
        return request;
    }
}
