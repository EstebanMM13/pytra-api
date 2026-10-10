package com.estebanmm13.pytra_api.steamsync;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.steamsync.client.SteamOwnedGame;
import com.estebanmm13.pytra_api.steamsync.client.SteamOwnedGamesResult;
import com.estebanmm13.pytra_api.steamsync.client.SteamWebApiClient;
import com.estebanmm13.pytra_api.steamsync.model.SteamLink;
import com.estebanmm13.pytra_api.steamsync.repository.SteamIgnoredAppRepository;
import com.estebanmm13.pytra_api.steamsync.repository.SteamLinkRepository;
import com.estebanmm13.pytra_api.steamsync.service.SteamLinkService;
import com.estebanmm13.pytra_api.steamsync.service.SteamSyncGuard;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
 * Steam sync end to end against a real Postgres, with the Steam HTTP client replaced by a mock
 * (so CI never calls Steam and every library shape is deterministic).
 */
class SteamIntegrationTests extends AbstractIntegrationTest {

    private static final String GAME_JSON = """
            {"name": "%s", "category": "%s"}""";

    @MockitoBean
    private SteamWebApiClient steamWebApiClient;

    @Autowired
    private SteamLinkRepository steamLinkRepository;
    @Autowired
    private SteamIgnoredAppRepository steamIgnoredAppRepository;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private GamePlatformLinkRepository gamePlatformLinkRepository;
    @Autowired
    private ExperienceRepository experienceRepository;
    @Autowired
    private OnlinePlaytimeRepository onlinePlaytimeRepository;
    @Autowired
    private SteamLinkService steamLinkService;
    @Autowired
    private SteamSyncGuard steamSyncGuard;
    @Autowired
    private DataSource dataSource;

    private User alice;
    private String aliceAuth;

    @BeforeEach
    void setUp() {
        alice = createVerifiedUser("steamalice");
        aliceAuth = bearer(alice);
        when(steamWebApiClient.isConfigured()).thenReturn(true);
    }

    // ---- Sync ----------------------------------------------------------------------------

    @Test
    void unknownAppBecomesPendingGame() throws Exception {
        link(alice);
        library(new SteamOwnedGame(620, "Portal 2", 600));

        sync(aliceAuth)
                .andExpect(jsonPath("$.gamesScanned").value(1))
                .andExpect(jsonPath("$.newGamesPending").value(1))
                .andExpect(jsonPath("$.profilePrivate").value(false));

        perform(get("/api/v1/integrations/steam/pending"), aliceAuth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Portal 2"))
                .andExpect(jsonPath("$[0].reviewStatus").value("PENDING_REVIEW"));
    }

    @Test
    void sameNameGameIsLinkedWithBaselineInsteadOfFailing() throws Exception {
        link(alice);
        Long gameId = createGame(aliceAuth, "portal 2", "SINGLEPLAYER");
        library(new SteamOwnedGame(620, "Portal 2", 600));

        sync(aliceAuth)
                .andExpect(jsonPath("$.linkedExisting").value(1))
                .andExpect(jsonPath("$.newGamesPending").value(0))
                .andExpect(jsonPath("$.errored").value(0));

        GamePlatformLink link = steamLinkOf(gameId);
        assertThat(link.getExternalId()).isEqualTo("620");
        // Existing hours were entered by hand: the Steam total is only the baseline, not imported.
        assertThat(link.getLastSyncedPlaytimeMinutes()).isEqualTo(600L);
        assertThat(experienceRepository.findAllByGameIdAndUserId(gameId, alice.getId())).isEmpty();

        // Only hours played after the link are added (60 min -> a new canonical run of 1h).
        library(new SteamOwnedGame(620, "Portal 2", 660));
        sync(aliceAuth).andExpect(jsonPath("$.gamesUpdated").value(1));

        List<Experience> runs = experienceRepository.findAllByGameIdAndUserId(gameId, alice.getId());
        assertThat(runs).singleElement().satisfies(run -> assertThat(run.getHours()).isEqualTo(1.0));
    }

    @Test
    void steamGamesGetHeaderCoverWithoutOverwritingUserCovers() throws Exception {
        link(alice);
        Long noCoverId = createGame(aliceAuth, "Half-Life 2", "SINGLEPLAYER");
        Long withCoverId = readId(perform(post("/api/v1/games"), aliceAuth,
                "{\"name\": \"Portal\", \"category\": \"SINGLEPLAYER\", \"coverImageUrl\": \"https://example.com/mine.png\"}")
                .andExpect(status().isCreated()).andReturn());
        library(new SteamOwnedGame(620, "Portal 2", 600),
                new SteamOwnedGame(220, "Half-Life 2", 60),
                new SteamOwnedGame(400, "Portal", 30));

        sync(aliceAuth)
                .andExpect(jsonPath("$.newGamesPending").value(1))
                .andExpect(jsonPath("$.linkedExisting").value(2));

        assertThat(gameRepository.findById(gameIdByName("Portal 2")).orElseThrow().getCoverImageUrl())
                .isEqualTo("https://cdn.cloudflare.steamstatic.com/steam/apps/620/header.jpg");
        assertThat(gameRepository.findById(noCoverId).orElseThrow().getCoverImageUrl())
                .isEqualTo("https://cdn.cloudflare.steamstatic.com/steam/apps/220/header.jpg");
        assertThat(gameRepository.findById(withCoverId).orElseThrow().getCoverImageUrl())
                .isEqualTo("https://example.com/mine.png");
    }

    @Test
    void oneFailingAppDoesNotAbortTheRestOfTheLibrary() throws Exception {
        link(alice);
        // Postgres rejects NUL bytes in text: this app's insert fails at the database.
        library(new SteamOwnedGame(1, "Good One", 10),
                new SteamOwnedGame(2, "Broken\u0000Name", 10),
                new SteamOwnedGame(3, "Good Two", 10));

        sync(aliceAuth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gamesScanned").value(3))
                .andExpect(jsonPath("$.newGamesPending").value(2))
                .andExpect(jsonPath("$.errored").value(1));

        assertThat(pendingNames()).containsExactlyInAnyOrder("Good One", "Good Two");
    }

    @Test
    void privateProfileIsReportedAndChangesNothing() throws Exception {
        link(alice);
        when(steamWebApiClient.getOwnedGames(anyString())).thenReturn(SteamOwnedGamesResult.privateProfile());

        sync(aliceAuth)
                .andExpect(jsonPath("$.profilePrivate").value(true))
                .andExpect(jsonPath("$.gamesScanned").value(0));

        assertThat(pendingNames()).isEmpty();
    }

    @Test
    void negativeDeltaMovesBaselineDownWithoutTouchingUserHours() throws Exception {
        link(alice);
        library(new SteamOwnedGame(730, "Counter-Strike 2", 600));
        sync(aliceAuth);
        Long gameId = pendingIds().getFirst();
        confirm(gameId, "Counter-Strike 2", "ONLINE");
        assertThat(onlineHours(gameId)).isEqualTo(10.0);

        // Refund / reset on Steam's side: total drops to 5h.
        library(new SteamOwnedGame(730, "Counter-Strike 2", 300));
        sync(aliceAuth).andExpect(jsonPath("$.gamesUpdated").value(0));
        assertThat(onlineHours(gameId)).isEqualTo(10.0);
        assertThat(steamLinkOf(gameId).getLastSyncedPlaytimeMinutes()).isEqualTo(300L);

        // Growth is measured from the new, lower baseline: +1h, not +6h.
        library(new SteamOwnedGame(730, "Counter-Strike 2", 360));
        sync(aliceAuth).andExpect(jsonPath("$.gamesUpdated").value(1));
        assertThat(onlineHours(gameId)).isEqualTo(11.0);
    }

    @Test
    void confirmingOnlineGameKeepsTheHigherOfManualAndSteamTotal() throws Exception {
        link(alice);
        library(new SteamOwnedGame(440, "Team Fortress 2", 600));
        sync(aliceAuth);
        Long gameId = pendingIds().getFirst();

        // The user typed 25h by hand before confirming: Steam's 10h must not be added on top.
        perform(put("/api/v1/games/{gameId}/online-playtime", gameId), aliceAuth, "{\"totalHours\": 25.0}")
                .andExpect(status().isOk());
        confirm(gameId, "Team Fortress 2", "ONLINE");

        assertThat(onlineHours(gameId)).isEqualTo(25.0);
    }

    @Test
    void concurrentSyncForSameUserIsRejected() throws Exception {
        link(alice);
        assertThat(steamSyncGuard.tryAcquire(alice.getId())).isTrue();
        try {
            perform(post("/api/v1/integrations/steam/sync"), aliceAuth)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(SteamIntegrationException.SYNC_IN_PROGRESS));
        } finally {
            steamSyncGuard.release(alice.getId());
        }
    }

    @Test
    void blankApiKeyAnswersSteamNotConfigured() throws Exception {
        link(alice);
        when(steamWebApiClient.isConfigured()).thenReturn(false);

        perform(post("/api/v1/integrations/steam/sync"), aliceAuth)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(SteamIntegrationException.NOT_CONFIGURED));

        perform(get("/api/v1/integrations/steam/status"), aliceAuth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void relinkDuringSteamCallIsDetectedAndNotReverted() throws Exception {
        link(alice);
        String newSteamId = uniqueSteamId(alice) + "7";
        // Simulates a relink committed by someone else while Steam is answering: written straight
        // to the DB, so the SteamLink this request loaded (open-in-view) is now stale.
        when(steamWebApiClient.getOwnedGames(anyString())).thenAnswer(invocation -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement update = connection.prepareStatement(
                         "UPDATE steam_links SET steam_id = ?, persona_name = 'new-persona' WHERE user_id = ?")) {
                update.setString(1, newSteamId);
                update.setLong(2, alice.getId());
                update.executeUpdate();
            }
            return new SteamOwnedGamesResult(false, List.of(new SteamOwnedGame(620, "Portal 2", 600)));
        });

        perform(post("/api/v1/integrations/steam/sync"), aliceAuth)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(SteamIntegrationException.LINK_CHANGED));

        assertThat(steamLinkRepository.findByUserId(alice.getId())).hasValueSatisfying(l -> {
            assertThat(l.getSteamId()).isEqualTo(newSteamId);
            assertThat(l.getPersonaName()).isEqualTo("new-persona");
        });
        assertThat(pendingIds()).isEmpty();
    }

    @Test
    void switchingSteamAccountWhileSyncRunsIsRefused() throws Exception {
        String steamId = link(alice);
        List<Throwable> relinkFailures = new ArrayList<>();
        when(steamWebApiClient.getOwnedGames(anyString())).thenAnswer(invocation -> {
            try {
                steamLinkService.upsertLink(alice.getId(), steamId + "7", "other");
            } catch (RuntimeException e) {
                relinkFailures.add(e);
            }
            return new SteamOwnedGamesResult(false, List.of(new SteamOwnedGame(620, "Portal 2", 600)));
        });

        sync(aliceAuth).andExpect(jsonPath("$.newGamesPending").value(1));

        assertThat(relinkFailures).singleElement().isInstanceOfSatisfying(SteamIntegrationException.class,
                e -> assertThat(e.getCode()).isEqualTo(SteamIntegrationException.SYNC_IN_PROGRESS));
        assertThat(steamLinkRepository.findByUserId(alice.getId()))
                .hasValueSatisfying(l -> assertThat(l.getSteamId()).isEqualTo(steamId));
    }

    @ParameterizedTest(name = "{0} releases the guard")
    @ValueSource(strings = {"apiKeyRejected", "rateLimited", "unavailable"})
    void failedSteamCallReleasesTheGuard(String failure) throws Exception {
        link(alice);
        SteamIntegrationException error = switch (failure) {
            case "apiKeyRejected" -> SteamIntegrationException.apiKeyRejected();
            case "rateLimited" -> SteamIntegrationException.rateLimited();
            default -> SteamIntegrationException.unavailable();
        };
        when(steamWebApiClient.getOwnedGames(anyString())).thenThrow(error);

        perform(post("/api/v1/integrations/steam/sync"), aliceAuth)
                .andExpect(status().is(error.getStatus().value()))
                .andExpect(jsonPath("$.message").value(error.getCode()));

        library(new SteamOwnedGame(620, "Portal 2", 600));
        sync(aliceAuth).andExpect(jsonPath("$.newGamesPending").value(1));
    }

    @Test
    void unlinkConfirmAndIgnoreWaitForRunningSync() throws Exception {
        link(alice);
        library(new SteamOwnedGame(620, "Portal 2", 600));
        sync(aliceAuth);
        Long pendingId = pendingIds().getFirst();

        assertThat(steamSyncGuard.tryAcquire(alice.getId())).isTrue();
        try {
            perform(delete("/api/v1/integrations/steam/link"), aliceAuth)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(SteamIntegrationException.SYNC_IN_PROGRESS));
            perform(put("/api/v1/integrations/steam/pending/{id}/confirm", pendingId), aliceAuth,
                    GAME_JSON.formatted("Portal 2", "SINGLEPLAYER"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(SteamIntegrationException.SYNC_IN_PROGRESS));
            perform(put("/api/v1/integrations/steam/pending/{id}/ignore", pendingId), aliceAuth)
                    .andExpect(status().isConflict());
        } finally {
            steamSyncGuard.release(alice.getId());
        }

        assertThat(steamLinkRepository.findByUserId(alice.getId())).isPresent();
        assertThat(pendingIds()).containsExactly(pendingId);
    }

    // ---- Ignore list ---------------------------------------------------------------------

    @Test
    void ignoredAppDisappearsAndIsNotRecreatedUntilUnignored() throws Exception {
        link(alice);
        library(new SteamOwnedGame(999, "Unwanted Demo", 5));
        sync(aliceAuth);
        Long gameId = pendingIds().getFirst();

        perform(put("/api/v1/integrations/steam/pending/{id}/ignore", gameId), aliceAuth)
                .andExpect(status().isNoContent());

        assertThat(pendingIds()).isEmpty();
        MvcResult games = perform(get("/api/v1/games"), aliceAuth).andExpect(status().isOk()).andReturn();
        assertThat(readIds(games, "$[*].id")).doesNotContain(gameId);

        sync(aliceAuth)
                .andExpect(jsonPath("$.ignored").value(1))
                .andExpect(jsonPath("$.newGamesPending").value(0));
        assertThat(pendingIds()).isEmpty();

        perform(get("/api/v1/integrations/steam/ignored"), aliceAuth)
                .andExpect(jsonPath("$[0].appId").value("999"))
                .andExpect(jsonPath("$[0].name").value("Unwanted Demo"));

        perform(delete("/api/v1/integrations/steam/ignored/{appId}", "999"), aliceAuth)
                .andExpect(status().isNoContent());
        sync(aliceAuth).andExpect(jsonPath("$.newGamesPending").value(1));
    }

    // ---- Status / unlink / relink --------------------------------------------------------

    @Test
    void statusReflectsLinkAndLastSync() throws Exception {
        perform(get("/api/v1/integrations/steam/status"), aliceAuth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(false))
                .andExpect(jsonPath("$.configured").value(true));

        String steamId = link(alice);
        library();
        sync(aliceAuth);

        perform(get("/api/v1/integrations/steam/status"), aliceAuth)
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.steamId").value(steamId))
                .andExpect(jsonPath("$.personaName").value("persona"))
                .andExpect(jsonPath("$.lastSyncAt").isNotEmpty());
    }

    @Test
    void unlinkKeepsConfirmedGamesAndDropsSteamOnlyData() throws Exception {
        link(alice);
        library(new SteamOwnedGame(10, "Kept Game", 120), new SteamOwnedGame(20, "Pending Game", 60),
                new SteamOwnedGame(30, "Ignored Game", 1));
        sync(aliceAuth);
        Long keptId = gameIdByName("Kept Game");
        confirm(keptId, "Kept Game", "ONLINE");
        perform(put("/api/v1/integrations/steam/pending/{id}/ignore", gameIdByName("Ignored Game")), aliceAuth)
                .andExpect(status().isNoContent());
        Long pendingId = gameIdByName("Pending Game");

        perform(delete("/api/v1/integrations/steam/link"), aliceAuth).andExpect(status().isNoContent());

        assertThat(steamLinkRepository.findByUserId(alice.getId())).isEmpty();
        assertThat(gameRepository.findByIdAndUserId(keptId, alice.getId()))
                .hasValueSatisfying(g -> assertThat(g.getReviewStatus()).isEqualTo(ReviewStatus.CONFIRMED));
        assertThat(onlineHours(keptId)).isEqualTo(2.0);
        assertThat(gameRepository.findById(pendingId)).isEmpty();
        assertThat(gamePlatformLinkRepository.findAllByUserIdAndPlatformWithGame(alice.getId(), ExternalPlatform.STEAM)).isEmpty();
        assertThat(steamIgnoredAppRepository.findAppIdsByUserId(alice.getId())).isEmpty();

        perform(get("/api/v1/integrations/steam/status"), aliceAuth).andExpect(jsonPath("$.linked").value(false));
        perform(delete("/api/v1/integrations/steam/link"), aliceAuth).andExpect(status().isNotFound());
    }

    @Test
    void relinkingDifferentAccountResetsBaselines() throws Exception {
        link(alice);
        library(new SteamOwnedGame(10, "Old Account Game", 120));
        sync(aliceAuth);

        steamLinkService.upsertLink(alice.getId(), uniqueSteamId(alice) + "9", "other");

        assertThat(gamePlatformLinkRepository.findAllByUserIdAndPlatformWithGame(alice.getId(), ExternalPlatform.STEAM)).isEmpty();
        assertThat(pendingIds()).isEmpty();
        assertThat(steamLinkRepository.findByUserId(alice.getId()))
                .hasValueSatisfying(l -> assertThat(l.getLastSyncAt()).isNull());
    }

    @Test
    void steamAccountLinkedToAnotherUserIsRejected() {
        String steamId = link(alice);
        User bob = createVerifiedUser("steambob");

        assertThatThrownBy(() -> steamLinkService.upsertLink(bob.getId(), steamId, "thief"))
                .isInstanceOfSatisfying(SteamIntegrationException.class,
                        e -> assertThat(e.getCode()).isEqualTo(SteamIntegrationException.ACCOUNT_ALREADY_LINKED));
        assertThat(steamLinkRepository.findByUserId(bob.getId())).isEmpty();
    }

    @Test
    void callbackFailureAlwaysRedirectsToClientWithFixedError() throws Exception {
        mockMvc.perform(get("/api/v1/integrations/steam/callback").param("state", "not-a-real-state"))
                .andExpect(status().isFound())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                        .startsWith("http://localhost:4200/oauth-callback")
                        .contains("error=steam_link_failed")
                        .contains("next=steam"));
    }

    // ---- Tenant isolation ----------------------------------------------------------------

    @Test
    void otherUserCannotIgnoreConfirmOrUnlinkAlicesSteamData() throws Exception {
        link(alice);
        library(new SteamOwnedGame(620, "Portal 2", 600));
        sync(aliceAuth);
        Long alicePendingId = pendingIds().getFirst();

        User bob = createVerifiedUser("steambob");
        String bobAuth = bearer(bob);

        perform(put("/api/v1/integrations/steam/pending/{id}/ignore", alicePendingId), bobAuth)
                .andExpect(status().isNotFound());
        perform(put("/api/v1/integrations/steam/pending/{id}/confirm", alicePendingId), bobAuth,
                GAME_JSON.formatted("Hijacked", "SINGLEPLAYER"))
                .andExpect(status().isNotFound());
        perform(delete("/api/v1/integrations/steam/link"), bobAuth).andExpect(status().isNotFound());
        perform(post("/api/v1/integrations/steam/sync"), bobAuth).andExpect(status().isNotFound());
        perform(get("/api/v1/integrations/steam/status"), bobAuth).andExpect(jsonPath("$.linked").value(false));

        assertThat(pendingIds()).containsExactly(alicePendingId);
        assertThat(steamLinkRepository.findByUserId(alice.getId())).isPresent();
        assertThat(steamIgnoredAppRepository.findAppIdsByUserId(alice.getId())).isEmpty();
    }

    // ---- Redesign data: pending app data and status counts --------------------------------

    @Test
    void pendingItemsExposeSteamAppIdPlaytimeAndLastPlayed() throws Exception {
        link(alice);
        Instant played = Instant.ofEpochSecond(1_700_000_000L);
        library(new SteamOwnedGame(620, "Portal 2", 600, played),
                new SteamOwnedGame(570, "Dota 2", 0, null));
        sync(aliceAuth);

        perform(get("/api/v1/integrations/steam/pending"), aliceAuth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Portal 2')].appId").value("620"))
                .andExpect(jsonPath("$[?(@.name == 'Portal 2')].steamPlaytimeMinutes").value(600))
                .andExpect(jsonPath("$[?(@.name == 'Portal 2')].reviewStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$[?(@.name == 'Dota 2')].appId").value("570"))
                .andExpect(jsonPath("$[?(@.name == 'Dota 2')].steamPlaytimeMinutes").value(0));
        assertThat(pendingLastPlayed("Portal 2")).isEqualTo(played);
        assertThat(pendingLastPlayed("Dota 2")).isNull();
    }

    @Test
    void resyncUpdatesLastPlayedAndPlaytimeButNeverErasesLastPlayed() throws Exception {
        link(alice);
        library(new SteamOwnedGame(620, "Portal 2", 600, Instant.ofEpochSecond(1_700_000_000L)));
        sync(aliceAuth);
        Long gameId = pendingIds().getFirst();

        Instant later = Instant.ofEpochSecond(1_710_000_000L);
        library(new SteamOwnedGame(620, "Portal 2", 660, later));
        sync(aliceAuth);
        assertThat(steamLinkOf(gameId).getLastPlayedAt()).isEqualTo(later);
        assertThat(pendingLastPlayed("Portal 2")).isEqualTo(later);
        perform(get("/api/v1/integrations/steam/pending"), aliceAuth)
                .andExpect(jsonPath("$[0].steamPlaytimeMinutes").value(660));

        // Steam omitting rtime_last_played (or sending 0) keeps the last known value.
        library(new SteamOwnedGame(620, "Portal 2", 660, null));
        sync(aliceAuth);
        assertThat(steamLinkOf(gameId).getLastPlayedAt()).isEqualTo(later);
    }

    @Test
    void statusCountsLinkedPendingAndIgnoredPerUser() throws Exception {
        perform(get("/api/v1/integrations/steam/status"), aliceAuth)
                .andExpect(jsonPath("$.linked").value(false))
                .andExpect(jsonPath("$.linkedGamesCount").value(0))
                .andExpect(jsonPath("$.pendingCount").value(0))
                .andExpect(jsonPath("$.ignoredCount").value(0));

        link(alice);
        createGame(aliceAuth, "Portal 2", "SINGLEPLAYER");
        library(new SteamOwnedGame(620, "Portal 2", 600),
                new SteamOwnedGame(570, "Dota 2", 10),
                new SteamOwnedGame(440, "Team Fortress 2", 10));
        sync(aliceAuth);
        perform(put("/api/v1/integrations/steam/pending/{id}/ignore", gameIdByName("Dota 2")), aliceAuth)
                .andExpect(status().isNoContent());

        // Portal 2 linked to the existing game + TF2 placeholder; Dota 2 ignored (its link deleted).
        perform(get("/api/v1/integrations/steam/status"), aliceAuth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.linkedGamesCount").value(2))
                .andExpect(jsonPath("$.pendingCount").value(1))
                .andExpect(jsonPath("$.ignoredCount").value(1));

        User bob = createVerifiedUser("steamcounts");
        String bobAuth = bearer(bob);
        perform(get("/api/v1/integrations/steam/status"), bobAuth)
                .andExpect(jsonPath("$.linkedGamesCount").value(0))
                .andExpect(jsonPath("$.pendingCount").value(0))
                .andExpect(jsonPath("$.ignoredCount").value(0));
        perform(get("/api/v1/integrations/steam/pending"), bobAuth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---- Helpers -------------------------------------------------------------------------

    /** Unique per user: steam_links.steam_id is unique and the DB is shared across tests. */
    private static String uniqueSteamId(User user) {
        return "7656119" + (10_000_000_000L + user.getId());
    }

    private String link(User user) {
        String steamId = uniqueSteamId(user);
        steamLinkRepository.save(SteamLink.builder()
                .userId(user.getId())
                .steamId(steamId)
                .personaName("persona")
                .linkedAt(LocalDateTime.now())
                .build());
        return steamId;
    }

    private void library(SteamOwnedGame... games) {
        // doReturn avoids invoking a previously stubbed throwing call while re-stubbing.
        doReturn(new SteamOwnedGamesResult(false, List.of(games))).when(steamWebApiClient).getOwnedGames(anyString());
    }

    private ResultActions sync(String auth) throws Exception {
        return perform(post("/api/v1/integrations/steam/sync"), auth).andExpect(status().isOk());
    }

    private Long createGame(String auth, String name, String category) throws Exception {
        return readId(perform(post("/api/v1/games"), auth, GAME_JSON.formatted(name, category))
                .andExpect(status().isCreated()).andReturn());
    }

    private void confirm(Long gameId, String name, String category) throws Exception {
        perform(put("/api/v1/integrations/steam/pending/{id}/confirm", gameId), aliceAuth,
                GAME_JSON.formatted(name, category))
                .andExpect(status().isOk());
    }

    private List<Long> pendingIds() throws Exception {
        return readIds(perform(get("/api/v1/integrations/steam/pending"), aliceAuth).andReturn(), "$[*].id");
    }

    private List<String> pendingNames() throws Exception {
        MvcResult result = perform(get("/api/v1/integrations/steam/pending"), aliceAuth).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$[*].name");
    }

    private Instant pendingLastPlayed(String name) throws Exception {
        MvcResult result = perform(get("/api/v1/integrations/steam/pending"), aliceAuth).andReturn();
        List<String> values = JsonPath.read(result.getResponse().getContentAsString(),
                "$[?(@.name == '" + name + "')].lastPlayedAt");
        assertThat(values).hasSize(1);
        return values.getFirst() == null ? null : Instant.parse(values.getFirst());
    }

    private Long gameIdByName(String name) {
        return gameRepository.findAllByUserId(alice.getId()).stream()
                .filter(g -> g.getName().equals(name))
                .findFirst().orElseThrow().getId();
    }

    private GamePlatformLink steamLinkOf(Long gameId) {
        return gamePlatformLinkRepository.findByUserIdAndGameIdAndPlatform(alice.getId(), gameId, ExternalPlatform.STEAM)
                .orElseThrow();
    }

    private double onlineHours(Long gameId) {
        return onlinePlaytimeRepository.findByGameIdAndUserId(gameId, alice.getId())
                .map(OnlinePlaytime::getTotalHours).orElse(0.0);
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder, String authorization) throws Exception {
        return mockMvc.perform(builder.header("Authorization", authorization));
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder, String authorization, String jsonBody) throws Exception {
        return mockMvc.perform(builder
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody));
    }
}
