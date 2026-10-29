package com.estebanmm13.pytra_api.steamsync;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.config.AppTimeZone;
import com.estebanmm13.pytra_api.diary.repository.DiaryEntryRepository;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.steamsync.client.SteamOwnedGame;
import com.estebanmm13.pytra_api.steamsync.client.SteamOwnedGamesResult;
import com.estebanmm13.pytra_api.steamsync.client.SteamWebApiClient;
import com.estebanmm13.pytra_api.steamsync.model.SteamLink;
import com.estebanmm13.pytra_api.steamsync.repository.SteamLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sync increments recorded in the diary as record-only auto lines, end to end against a real Postgres
 * (Steam client mocked). The sync runs with "now" and no last-played time, so the lines land on today.
 */
class SyncedDiaryIntegrationTests extends AbstractIntegrationTest {

    private static final String GAME_JSON = """
            {"name": "%s", "category": "%s"}""";

    @MockitoBean
    private SteamWebApiClient steamWebApiClient;

    @Autowired
    private SteamLinkRepository steamLinkRepository;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private ExperienceRepository experienceRepository;
    @Autowired
    private OnlinePlaytimeRepository onlinePlaytimeRepository;
    @Autowired
    private GamePlatformLinkRepository gamePlatformLinkRepository;
    @Autowired
    private AppTimeZone appTimeZone;
    @MockitoSpyBean
    private DiaryEntryRepository diaryEntryRepository;

    private User alice;
    private String aliceAuth;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        alice = createVerifiedUser("diarysync");
        aliceAuth = bearer(alice);
        // Sync-written lines land on the users' day (app.time-zone), not the server's.
        today = LocalDate.now(appTimeZone.zone());
        when(steamWebApiClient.isConfigured()).thenReturn(true);
        link(alice);
    }

    @Test
    void firstImportAndConfirmRecordNothing() throws Exception {
        library(new SteamOwnedGame(730, "Counter-Strike 2", 600 * 60));
        sync();
        confirm(gameIdByName("Counter-Strike 2"), "Counter-Strike 2", "ONLINE");

        performAs(aliceAuth, get("/api/v1/diary/{date}", today)).andExpect(status().isNotFound());
        performAs(aliceAuth, get("/api/v1/stats/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHours").value(0.0));
    }

    @Test
    void syncIncrementCreatesARecordOnlyAutoLineAndASecondSyncMergesIntoIt() throws Exception {
        library(new SteamOwnedGame(730, "Counter-Strike 2", 600));
        sync();
        Long gameId = gameIdByName("Counter-Strike 2");
        confirm(gameId, "Counter-Strike 2", "ONLINE");

        library(new SteamOwnedGame(730, "Counter-Strike 2", 660));
        sync();

        performAs(aliceAuth, get("/api/v1/diary/{date}", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games.length()").value(1))
                .andExpect(jsonPath("$.games[0].gameId").value(gameId))
                .andExpect(jsonPath("$.games[0].hours").value(1.0))
                .andExpect(jsonPath("$.games[0].experienceId").isEmpty())
                .andExpect(jsonPath("$.games[0].countsHours").value(false))
                .andExpect(jsonPath("$.games[0].autoSynced").value(true));

        // Same day, another sync: one line, delta added.
        library(new SteamOwnedGame(730, "Counter-Strike 2", 690));
        sync();

        performAs(aliceAuth, get("/api/v1/diary/{date}", today))
                .andExpect(jsonPath("$.games.length()").value(1))
                .andExpect(jsonPath("$.games[0].hours").value(1.5))
                .andExpect(jsonPath("$.totalHours").value(1.5));
        performAs(aliceAuth, get("/api/v1/games/{gameId}/diary", gameId))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].autoSynced").value(true));

        // A drop (refund) and the same total again add nothing.
        library(new SteamOwnedGame(730, "Counter-Strike 2", 630));
        sync();
        library(new SteamOwnedGame(730, "Counter-Strike 2", 630));
        sync();
        performAs(aliceAuth, get("/api/v1/diary/{date}", today))
                .andExpect(jsonPath("$.games[0].hours").value(1.5));
    }

    @Test
    void autoLinesNeverChangeTotalsOrYearStatsButShowInTheActivityHeatmap() throws Exception {
        library(new SteamOwnedGame(730, "Counter-Strike 2", 600));
        sync();
        confirm(gameIdByName("Counter-Strike 2"), "Counter-Strike 2", "ONLINE");
        Long portalId = createGame("Portal 2", "SINGLEPLAYER");
        library(new SteamOwnedGame(730, "Counter-Strike 2", 600), new SteamOwnedGame(620, "Portal 2", 300));
        sync(); // Portal 2 linked by name: its 5h are only the baseline.

        library(new SteamOwnedGame(730, "Counter-Strike 2", 720), new SteamOwnedGame(620, "Portal 2", 390));
        sync();

        // Sync hours: online 10h + 2h, Portal 2 canonical run 1.5h. Nothing on top from the diary.
        performAs(aliceAuth, get("/api/v1/stats/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOnlineHours").value(12.0))
                .andExpect(jsonPath("$.totalSingleplayerHours").value(1.5));
        performAs(aliceAuth, get("/api/v1/stats/years/{year}", today.getYear()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onlineHours").value(0.0));

        List<Experience> runs = experienceRepository.findAllByGameIdAndUserId(portalId, alice.getId());
        assertThat(runs).singleElement().satisfies(run -> assertThat(run.getHours()).isEqualTo(1.5));
        performAs(aliceAuth, get("/api/v1/games/{gameId}/diary", portalId))
                .andExpect(jsonPath("$[0].experienceId").value(runs.getFirst().getId()))
                .andExpect(jsonPath("$[0].countsHours").value(false));

        // The heatmap records every diary line: 2h + 1.5h today.
        performAs(aliceAuth, get("/api/v1/stats/activity"))
                .andExpect(jsonPath("$.totalHours").value(3.5))
                .andExpect(jsonPath("$.activeDays").value(1));
    }

    @Test
    void userWrittenLineIsNeverTouched() throws Exception {
        library(new SteamOwnedGame(730, "Counter-Strike 2", 600));
        sync();
        Long gameId = gameIdByName("Counter-Strike 2");
        confirm(gameId, "Counter-Strike 2", "ONLINE");
        performAs(aliceAuth, put("/api/v1/diary/{date}", today),
                "{\"games\": [{\"gameId\": " + gameId + ", \"hours\": 3, \"note\": \"ranked\"}]}")
                .andExpect(status().isOk());

        library(new SteamOwnedGame(730, "Counter-Strike 2", 720));
        sync();

        performAs(aliceAuth, get("/api/v1/diary/{date}", today))
                .andExpect(jsonPath("$.games.length()").value(1))
                .andExpect(jsonPath("$.games[0].hours").value(3.0))
                .andExpect(jsonPath("$.games[0].note").value("ranked"))
                .andExpect(jsonPath("$.games[0].autoSynced").value(false));
    }

    @Test
    void autoLinesStayWithTheirOwner() throws Exception {
        library(new SteamOwnedGame(730, "Counter-Strike 2", 600));
        sync();
        Long gameId = gameIdByName("Counter-Strike 2");
        confirm(gameId, "Counter-Strike 2", "ONLINE");
        library(new SteamOwnedGame(730, "Counter-Strike 2", 660));
        sync();

        User bob = createVerifiedUser("diarysyncbob");
        String bobAuth = bearer(bob);
        performAs(bobAuth, get("/api/v1/diary/{date}", today)).andExpect(status().isNotFound());
        performAs(bobAuth, get("/api/v1/diary").param("from", today.toString()).param("to", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        performAs(bobAuth, get("/api/v1/games/{gameId}/diary", gameId)).andExpect(status().isNotFound());
        performAs(bobAuth, get("/api/v1/stats/activity")).andExpect(jsonPath("$.totalHours").value(0.0));
    }

    @Test
    void aFailingDiaryWriteNeverRollsBackTheSync() throws Exception {
        library(new SteamOwnedGame(730, "Counter-Strike 2", 600), new SteamOwnedGame(620, "Portal 2", 60));
        sync();
        Long csId = gameIdByName("Counter-Strike 2");
        Long portalId = gameIdByName("Portal 2");
        confirm(csId, "Counter-Strike 2", "ONLINE");
        confirm(portalId, "Portal 2", "ONLINE");
        // E.g. the user saving the same day at that moment, or a PSN sync racing for the day's entry.
        doThrow(new DataIntegrityViolationException("uq_diary_entries_user_date"))
                .when(diaryEntryRepository).findForUpdate(anyLong(), any());

        library(new SteamOwnedGame(730, "Counter-Strike 2", 660), new SteamOwnedGame(620, "Portal 2", 120));
        sync().andExpect(jsonPath("$.gamesUpdated").value(2));

        // Hours and baselines of every game committed; only the diary record was dropped.
        assertThat(onlineHours(csId)).isEqualTo(11.0);
        assertThat(onlineHours(portalId)).isEqualTo(2.0);
        assertThat(gamePlatformLinkRepository
                .findByUserIdAndGameIdAndPlatform(alice.getId(), csId, ExternalPlatform.STEAM).orElseThrow()
                .getLastSyncedPlaytimeMinutes()).isEqualTo(660L);
        performAs(aliceAuth, get("/api/v1/diary/{date}", today)).andExpect(status().isNotFound());

        // The next sync records again.
        reset(diaryEntryRepository);
        library(new SteamOwnedGame(730, "Counter-Strike 2", 690), new SteamOwnedGame(620, "Portal 2", 120));
        sync();
        performAs(aliceAuth, get("/api/v1/diary/{date}", today))
                .andExpect(jsonPath("$.games.length()").value(1))
                .andExpect(jsonPath("$.games[0].hours").value(0.5));
    }

    @Test
    void savingTheDayBackAsTheWebDoesKeepsTheAutoLine() throws Exception {
        library(new SteamOwnedGame(730, "Counter-Strike 2", 600));
        sync();
        Long gameId = gameIdByName("Counter-Strike 2");
        confirm(gameId, "Counter-Strike 2", "ONLINE");
        library(new SteamOwnedGame(730, "Counter-Strike 2", 690));
        sync();

        // pytra-web sends hours as JSON numbers (1.50 stored -> 1.5 sent) and adds a note to the day.
        performAs(aliceAuth, put("/api/v1/diary/{date}", today),
                "{\"note\": \"Good night\", \"games\": [{\"gameId\": " + gameId + ", \"hours\": 1.5, \"note\": null}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games[0].autoSynced").value(true))
                .andExpect(jsonPath("$.games[0].countsHours").value(false));
        assertThat(onlineHours(gameId)).isEqualTo(11.5);

        // A later sync still merges into it.
        library(new SteamOwnedGame(730, "Counter-Strike 2", 720));
        sync();
        performAs(aliceAuth, get("/api/v1/diary/{date}", today))
                .andExpect(jsonPath("$.note").value("Good night"))
                .andExpect(jsonPath("$.games[0].hours").value(2.0));
    }

    // ---- Helpers -------------------------------------------------------------------------

    private double onlineHours(Long gameId) {
        return onlinePlaytimeRepository.findByGameIdAndUserId(gameId, alice.getId())
                .map(OnlinePlaytime::getTotalHours).orElse(0.0);
    }

    private void link(User user) {
        steamLinkRepository.save(SteamLink.builder()
                .userId(user.getId())
                .steamId("7656119" + (20_000_000_000L + user.getId()))
                .personaName("persona")
                .linkedAt(LocalDateTime.now())
                .build());
    }

    private void library(SteamOwnedGame... games) {
        doReturn(new SteamOwnedGamesResult(false, List.of(games))).when(steamWebApiClient).getOwnedGames(anyString());
    }

    private ResultActions sync() throws Exception {
        return performAs(aliceAuth, post("/api/v1/integrations/steam/sync")).andExpect(status().isOk());
    }

    private Long createGame(String name, String category) throws Exception {
        return readId(performAs(aliceAuth, post("/api/v1/games"), GAME_JSON.formatted(name, category))
                .andExpect(status().isCreated()).andReturn());
    }

    private void confirm(Long gameId, String name, String category) throws Exception {
        performAs(aliceAuth, put("/api/v1/integrations/steam/pending/{id}/confirm", gameId),
                GAME_JSON.formatted(name, category))
                .andExpect(status().isOk());
    }

    private Long gameIdByName(String name) {
        return gameRepository.findAllByUserId(alice.getId()).stream()
                .filter(g -> g.getName().equals(name))
                .findFirst().orElseThrow().getId();
    }
}
