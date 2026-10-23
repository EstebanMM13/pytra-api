package com.estebanmm13.pytra_api.psnsync;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.error.PsnIntegrationException;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.psnsync.client.PsnAccount;
import com.estebanmm13.pytra_api.psnsync.client.PsnApiClient;
import com.estebanmm13.pytra_api.psnsync.client.PsnPlayedGamesResult;
import com.estebanmm13.pytra_api.psnsync.client.PsnPlayedTitle;
import com.estebanmm13.pytra_api.psnsync.repository.PsnIgnoredTitleRepository;
import com.estebanmm13.pytra_api.psnsync.repository.PsnLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PSN sync end to end against a real Postgres, with the PSN HTTP client replaced by a mock
 * (so CI never calls Sony and every game list is deterministic).
 */
class PsnIntegrationTests extends AbstractIntegrationTest {

    private static final String GAME_JSON = """
            {"name": "%s", "category": "%s"}""";
    private static final Instant LAST_PLAYED = Instant.parse("2024-08-03T19:28:27Z");

    @MockitoBean
    private PsnApiClient psnApiClient;

    @Autowired
    private PsnLinkRepository psnLinkRepository;
    @Autowired
    private PsnIgnoredTitleRepository psnIgnoredTitleRepository;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private GamePlatformLinkRepository gamePlatformLinkRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User alice;
    private String aliceAuth;

    @BeforeEach
    void setUp() {
        alice = createVerifiedUser("psnalice");
        aliceAuth = bearer(alice);
        when(psnApiClient.isConfigured()).thenReturn(true);
    }

    // ---- Link ----------------------------------------------------------------------------

    @Test
    void linkResolvesTheOnlineIdAndStatusShowsIt() throws Exception {
        when(psnApiClient.findAccount("EstebanK13")).thenReturn(new PsnAccount(accountId(alice), "EstebanK13"));

        performAs(aliceAuth, put("/api/v1/integrations/psn/link"), "{\"onlineId\": \"EstebanK13\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.onlineId").value("EstebanK13"))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.serviceAuthError").value(nullValue()));

        performAs(aliceAuth, get("/api/v1/integrations/psn/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId(alice)))
                .andExpect(jsonPath("$.lastSyncAt").value(nullValue()));
    }

    @Test
    void unknownOnlineIdIsNotLinked() throws Exception {
        when(psnApiClient.findAccount("Nobody123")).thenThrow(PsnIntegrationException.userNotFound());

        performAs(aliceAuth, put("/api/v1/integrations/psn/link"), "{\"onlineId\": \"Nobody123\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("PSN_USER_NOT_FOUND"));
        assertThat(psnLinkRepository.findByUserId(alice.getId())).isEmpty();
    }

    @Test
    void malformedOnlineIdIsRejected() throws Exception {
        performAs(aliceAuth, put("/api/v1/integrations/psn/link"), "{\"onlineId\": \"no spaces allowed\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("PSN_INVALID_ONLINE_ID"));
    }

    @Test
    void notConfiguredServerAnswers503() throws Exception {
        link(alice);
        when(psnApiClient.isConfigured()).thenReturn(false);

        performAs(aliceAuth, post("/api/v1/integrations/psn/sync"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("PSN_NOT_CONFIGURED"));
    }

    // ---- Sync ----------------------------------------------------------------------------

    @Test
    void ps4AndPs5TitlesBecomeOnePendingGameOnlyInThePsnQueue() throws Exception {
        link(alice);
        library(title("PPSA01234_00", "Ghost of Tsushima", ExternalPlatform.PSN_PS5, 120),
                title("CUSA13323_00", "Ghost of Tsushima", ExternalPlatform.PSN_PS4, 600));

        sync(aliceAuth)
                .andExpect(jsonPath("$.gamesScanned").value(2))
                .andExpect(jsonPath("$.newGamesPending").value(1))
                .andExpect(jsonPath("$.linkedExisting").value(1))
                .andExpect(jsonPath("$.profilePrivate").value(false));

        performAs(aliceAuth, get("/api/v1/integrations/psn/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Ghost of Tsushima"))
                .andExpect(jsonPath("$[0].reviewStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$[0].titleId").value("PPSA01234_00"))
                .andExpect(jsonPath("$[0].psnPlatform").value("PS5"))
                .andExpect(jsonPath("$[0].psnPlaytimeMinutes").value(720));

        performAs(aliceAuth, get("/api/v1/integrations/steam/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        performAs(aliceAuth, get("/api/v1/integrations/psn/status"))
                .andExpect(jsonPath("$.linkedGamesCount").value(2))
                .andExpect(jsonPath("$.pendingCount").value(1));
    }

    @Test
    void sameNameTitlesOfDifferentConceptsBecomeTwoPendingGames() throws Exception {
        link(alice);
        library(title("CUSA02636_00", "Until Dawn™", ExternalPlatform.PSN_PS4, 600, 201L),
                title("PPSA21000_00", "Until Dawn™", ExternalPlatform.PSN_PS5, 300, 202L));

        sync(aliceAuth)
                .andExpect(jsonPath("$.newGamesPending").value(2))
                .andExpect(jsonPath("$.linkedExisting").value(0));

        assertThat(jdbcTemplate.queryForList(
                "SELECT g.name AS name, g.imported_name AS imported, l.platform AS platform, l.psn_concept_id AS concept "
                        + "FROM games g JOIN game_platform_links l ON l.game_id = g.id WHERE g.user_id = ? ORDER BY l.platform",
                alice.getId()))
                .extracting(row -> row.get("name"), row -> row.get("imported"), row -> row.get("platform"),
                        row -> ((Number) row.get("concept")).longValue())
                .containsExactly(
                        tuple("Until Dawn", "Until Dawn", "PSN_PS4", 201L),
                        tuple("Until Dawn (PS5)", "Until Dawn", "PSN_PS5", 202L));
    }

    @Test
    void pendingGameMergedBeforeConceptsWereKnownIsSplitOnTheNextSync() throws Exception {
        link(alice);
        library(title("CUSA02636_00", "Until Dawn", ExternalPlatform.PSN_PS4, 600),
                title("PPSA21000_00", "Until Dawn", ExternalPlatform.PSN_PS5, 300));
        sync(aliceAuth).andExpect(jsonPath("$.newGamesPending").value(1));
        Long mergedId = gameIdByName("Until Dawn");

        library(title("CUSA02636_00", "Until Dawn", ExternalPlatform.PSN_PS4, 600, 201L),
                title("PPSA21000_00", "Until Dawn", ExternalPlatform.PSN_PS5, 300, 202L));
        sync(aliceAuth).andExpect(jsonPath("$.newGamesPending").value(1));

        Long splitId = gameIdByName("Until Dawn (PS5)");
        assertThat(gamePlatformLinkRepository.findAllByUserIdAndGameId(alice.getId(), mergedId))
                .extracting(link -> link.getPlatform()).containsExactly(ExternalPlatform.PSN_PS4);
        assertThat(gamePlatformLinkRepository.findAllByUserIdAndGameId(alice.getId(), splitId))
                .extracting(link -> link.getPlatform()).containsExactly(ExternalPlatform.PSN_PS5);
        performAs(aliceAuth, get("/api/v1/integrations/psn/pending"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void confirmingASingleplayerGameCreatesOneRunPerConsole() throws Exception {
        link(alice);
        library(title("PPSA01234_00", "Ghost of Tsushima", ExternalPlatform.PSN_PS5, 120),
                title("CUSA13323_00", "Ghost of Tsushima", ExternalPlatform.PSN_PS4, 600));
        sync(aliceAuth);
        Long gameId = gameIdByName("Ghost of Tsushima");

        performAs(aliceAuth, put("/api/v1/integrations/psn/pending/{id}/confirm", gameId),
                GAME_JSON.formatted("Ghost of Tsushima", "SINGLEPLAYER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("CONFIRMED"));

        List<Map<String, Object>> runs = jdbcTemplate.queryForList(
                "SELECT p.legacy_code AS code, e.hours AS hours FROM experiences e "
                        + "JOIN platforms p ON p.id = e.platform_id WHERE e.game_id = ? ORDER BY p.legacy_code",
                gameId);
        assertThat(runs).extracting(row -> row.get("code"), row -> ((Number) row.get("hours")).doubleValue())
                .containsExactly(
                        tuple("PS4", 10.0),
                        tuple("PS5", 2.0));

        // Later playtime on PS5 only grows the PS5 run.
        library(title("PPSA01234_00", "Ghost of Tsushima", ExternalPlatform.PSN_PS5, 180),
                title("CUSA13323_00", "Ghost of Tsushima", ExternalPlatform.PSN_PS4, 600));
        sync(aliceAuth).andExpect(jsonPath("$.gamesUpdated").value(1));
        Double ps5Hours = jdbcTemplate.queryForObject(
                "SELECT e.hours FROM experiences e JOIN platforms p ON p.id = e.platform_id "
                        + "WHERE e.game_id = ? AND p.legacy_code = 'PS5'", Double.class, gameId);
        assertThat(ps5Hours).isEqualTo(3.0);
    }

    @Test
    void sameNameGameIsLinkedWithBaselineInsteadOfImportingHours() throws Exception {
        link(alice);
        Long gameId = createGameVia(aliceAuth, "bloodborne", "");
        library(title("CUSA00900_00", "Bloodborne", ExternalPlatform.PSN_PS4, 3000));

        sync(aliceAuth).andExpect(jsonPath("$.linkedExisting").value(1));

        assertThat(gamePlatformLinkRepository.findByUserIdAndGameIdAndPlatform(alice.getId(), gameId, ExternalPlatform.PSN_PS4))
                .hasValueSatisfying(link -> assertThat(link.getLastSyncedPlaytimeMinutes()).isEqualTo(3000L));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM experiences WHERE game_id = ?", Long.class, gameId))
                .isZero();
    }

    @Test
    void privateProfileChangesNothing() throws Exception {
        link(alice);
        doReturn(PsnPlayedGamesResult.privateProfile()).when(psnApiClient).getPlayedGames(anyString());

        sync(aliceAuth).andExpect(jsonPath("$.profilePrivate").value(true));

        assertThat(gameRepository.findAllByUserId(alice.getId())).isEmpty();
    }

    @Test
    void ignoredTitleIsNeverRecreatedUntilUnignored() throws Exception {
        link(alice);
        library(title("CUSA00900_00", "Bloodborne", ExternalPlatform.PSN_PS4, 60));
        sync(aliceAuth);
        Long gameId = gameIdByName("Bloodborne");

        performAs(aliceAuth, put("/api/v1/integrations/psn/pending/{id}/ignore", gameId))
                .andExpect(status().isNoContent());
        performAs(aliceAuth, get("/api/v1/integrations/psn/ignored"))
                .andExpect(jsonPath("$[0].titleId").value("CUSA00900_00"))
                .andExpect(jsonPath("$[0].platform").value("PS4"))
                .andExpect(jsonPath("$[0].name").value("Bloodborne"));

        sync(aliceAuth).andExpect(jsonPath("$.ignored").value(1)).andExpect(jsonPath("$.newGamesPending").value(0));

        performAs(aliceAuth, delete("/api/v1/integrations/psn/ignored/{titleId}", "CUSA00900_00"))
                .andExpect(status().isNoContent());
        sync(aliceAuth).andExpect(jsonPath("$.newGamesPending").value(1));
    }

    @Test
    void unlinkDeletesPendingPlaceholdersAndTheIgnoreList() throws Exception {
        link(alice);
        library(title("CUSA00900_00", "Bloodborne", ExternalPlatform.PSN_PS4, 60),
                title("CUSA00001_00", "Knack", ExternalPlatform.PSN_PS4, 60));
        sync(aliceAuth);
        performAs(aliceAuth, put("/api/v1/integrations/psn/pending/{id}/ignore", gameIdByName("Knack")))
                .andExpect(status().isNoContent());

        performAs(aliceAuth, delete("/api/v1/integrations/psn/link")).andExpect(status().isNoContent());

        assertThat(gameRepository.findAllByUserId(alice.getId())).isEmpty();
        assertThat(psnIgnoredTitleRepository.countByUserId(alice.getId())).isZero();
        performAs(aliceAuth, get("/api/v1/integrations/psn/status"))
                .andExpect(jsonPath("$.linked").value(false))
                .andExpect(jsonPath("$.linkedGamesCount").value(0));
    }

    @Test
    void anotherUserCannotTouchThePendingQueue() throws Exception {
        link(alice);
        library(title("CUSA00900_00", "Bloodborne", ExternalPlatform.PSN_PS4, 60));
        sync(aliceAuth);
        Long gameId = gameIdByName("Bloodborne");
        String bobAuth = bearer(createVerifiedUser("psnbob"));

        performAs(bobAuth, put("/api/v1/integrations/psn/pending/{id}/ignore", gameId))
                .andExpect(status().isNotFound());
        performAs(bobAuth, put("/api/v1/integrations/psn/pending/{id}/confirm", gameId),
                GAME_JSON.formatted("Bloodborne", "SINGLEPLAYER"))
                .andExpect(status().isNotFound());
        performAs(bobAuth, get("/api/v1/integrations/psn/pending"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---- Helpers -------------------------------------------------------------------------

    private static String accountId(User user) {
        return String.valueOf(900_000_000_000L + user.getId());
    }

    private void link(User user) throws Exception {
        when(psnApiClient.findAccount("Player" + user.getId()))
                .thenReturn(new PsnAccount(accountId(user), "Player" + user.getId()));
        performAs(bearer(user), put("/api/v1/integrations/psn/link"), "{\"onlineId\": \"Player" + user.getId() + "\"}")
                .andExpect(status().isOk());
    }

    private void library(PsnPlayedTitle... titles) {
        // doReturn avoids invoking a previously stubbed throwing call while re-stubbing.
        doReturn(new PsnPlayedGamesResult(false, List.of(titles))).when(psnApiClient).getPlayedGames(anyString());
    }

    private static PsnPlayedTitle title(String titleId, String name, ExternalPlatform platform, long minutes) {
        return new PsnPlayedTitle(titleId, name, "https://image.api.playstation.com/" + titleId + ".png",
                platform, 1, minutes, null, LAST_PLAYED);
    }

    private static PsnPlayedTitle title(String titleId, String name, ExternalPlatform platform, long minutes,
                                        Long conceptId) {
        return new PsnPlayedTitle(titleId, name, "https://image.api.playstation.com/" + titleId + ".png",
                platform, 1, minutes, null, LAST_PLAYED, conceptId);
    }

    private ResultActions sync(String auth) throws Exception {
        return performAs(auth, post("/api/v1/integrations/psn/sync")).andExpect(status().isOk());
    }

    private Long gameIdByName(String name) {
        return gameRepository.findAllByUserId(alice.getId()).stream()
                .filter(g -> g.getName().equals(name))
                .findFirst().orElseThrow().getId();
    }
}
