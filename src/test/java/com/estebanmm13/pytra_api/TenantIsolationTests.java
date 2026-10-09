package com.estebanmm13.pytra_api;

import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.steamsync.model.SteamLink;
import com.estebanmm13.pytra_api.steamsync.repository.SteamLinkRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Multi-tenant isolation: user B must never read, modify or delete user A's data.
 *
 * <p>API convention: a resource that exists but belongs to someone else is indistinguishable
 * from a missing one, so every cross-user access must answer 404 (never 403, which would confirm
 * the id exists, and never 200). Each write attempt is followed by a check, as A, that A's data
 * is unchanged.
 */
class TenantIsolationTests extends AbstractIntegrationTest {

    private static final String GAME_JSON = """
            {"name": "%s", "category": "SINGLEPLAYER"%s}""";
    private static final String EXPERIENCE_JSON = """
            {"runLabel": "%s", "status": "COMPLETADO", "platform": "PC", "rating": 9, "hours": 42.5, "year": 2024}""";
    private static final String ONLINE_PLAYTIME_JSON = """
            {"totalHours": %s, "generalRating": 7}""";

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GamePlatformLinkRepository gamePlatformLinkRepository;

    @Autowired
    private SteamLinkRepository steamLinkRepository;

    private User alice;
    private User bob;
    private String aliceAuth;
    private String bobAuth;

    // Alice's data, created through the API like a real client would.
    private Long aliceSagaId;
    private Long aliceGameId;
    private Long aliceExperienceId;

    @BeforeEach
    void createAliceData() throws Exception {
        alice = createVerifiedUser("alice");
        bob = createVerifiedUser("bob");
        aliceAuth = bearer(alice);
        bobAuth = bearer(bob);

        aliceSagaId = readId(perform(post("/api/v1/sagas"), aliceAuth, "{\"name\": \"Alice Saga\"}")
                .andExpect(status().isCreated()).andReturn());

        aliceGameId = readId(perform(post("/api/v1/games"), aliceAuth,
                GAME_JSON.formatted("Alice Game", ", \"sagaId\": " + aliceSagaId))
                .andExpect(status().isCreated()).andReturn());

        aliceExperienceId = readId(perform(post("/api/v1/games/{gameId}/experiences", aliceGameId), aliceAuth,
                EXPERIENCE_JSON.formatted("Alice run"))
                .andExpect(status().isCreated()).andReturn());

        perform(put("/api/v1/games/{gameId}/online-playtime", aliceGameId), aliceAuth,
                ONLINE_PLAYTIME_JSON.formatted("100.0"))
                .andExpect(status().isOk());
    }

    static Stream<Arguments> protectedEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/v1/games"),
                Arguments.of(HttpMethod.GET, "/api/v1/games/1"),
                Arguments.of(HttpMethod.POST, "/api/v1/games"),
                Arguments.of(HttpMethod.PUT, "/api/v1/games/1"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/games/1"),
                Arguments.of(HttpMethod.GET, "/api/v1/sagas"),
                Arguments.of(HttpMethod.GET, "/api/v1/sagas/1"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/sagas/1"),
                Arguments.of(HttpMethod.GET, "/api/v1/genres"),
                Arguments.of(HttpMethod.POST, "/api/v1/genres"),
                Arguments.of(HttpMethod.GET, "/api/v1/games/1/experiences"),
                Arguments.of(HttpMethod.POST, "/api/v1/games/1/experiences"),
                Arguments.of(HttpMethod.GET, "/api/v1/experiences/1"),
                Arguments.of(HttpMethod.PUT, "/api/v1/experiences/1"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/experiences/1"),
                Arguments.of(HttpMethod.GET, "/api/v1/games/1/online-playtime"),
                Arguments.of(HttpMethod.PUT, "/api/v1/games/1/online-playtime"),
                Arguments.of(HttpMethod.GET, "/api/v1/stats/summary"),
                Arguments.of(HttpMethod.GET, "/api/v1/stats/by-year"),
                Arguments.of(HttpMethod.GET, "/api/v1/stats/by-saga"),
                Arguments.of(HttpMethod.GET, "/api/v1/stats/by-genre"),
                Arguments.of(HttpMethod.GET, "/api/v1/stats/top-rated"),
                Arguments.of(HttpMethod.GET, "/api/v1/stats/most-played/singleplayer"),
                Arguments.of(HttpMethod.GET, "/api/v1/stats/most-played/online"),
                Arguments.of(HttpMethod.GET, "/api/v1/stats/years"),
                Arguments.of(HttpMethod.GET, "/api/v1/stats/years/2024"),
                Arguments.of(HttpMethod.PUT, "/api/v1/stats/years/2024/note"),
                Arguments.of(HttpMethod.GET, "/api/v1/stats/in-progress"),
                Arguments.of(HttpMethod.GET, "/api/v1/users/me"),
                Arguments.of(HttpMethod.PATCH, "/api/v1/users/me"),
                Arguments.of(HttpMethod.GET, "/api/v1/users/me/export"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/users/me"),
                Arguments.of(HttpMethod.POST, "/api/v1/integrations/steam/connect-token"),
                Arguments.of(HttpMethod.POST, "/api/v1/integrations/steam/sync"),
                Arguments.of(HttpMethod.GET, "/api/v1/integrations/steam/pending"),
                Arguments.of(HttpMethod.PUT, "/api/v1/integrations/steam/pending/1/confirm"),
                Arguments.of(HttpMethod.PUT, "/api/v1/integrations/steam/pending/1/ignore"),
                Arguments.of(HttpMethod.GET, "/api/v1/integrations/steam/status"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/integrations/steam/link"),
                Arguments.of(HttpMethod.GET, "/api/v1/integrations/steam/ignored"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/integrations/steam/ignored/620")
        );
    }

    @Nested
    class Authentication {

        @ParameterizedTest(name = "{0} {1} without token -> 401")
        @MethodSource("com.estebanmm13.pytra_api.TenantIsolationTests#protectedEndpoints")
        void rejectsRequestsWithoutToken(HttpMethod method, String path) throws Exception {
            mockMvc.perform(request(method, path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @ParameterizedTest(name = "{0} {1} with a forged token -> 401")
        @MethodSource("com.estebanmm13.pytra_api.TenantIsolationTests#protectedEndpoints")
        void rejectsTokensSignedWithAnotherKey(HttpMethod method, String path) throws Exception {
            // Valid structure, signed with a key the API doesn't know, claiming to be Alice.
            String forged = Jwts.builder()
                    .subject(alice.getUsername())
                    .claim("userId", alice.getId())
                    .claim("role", "USER")
                    .signWith(Keys.hmacShaKeyFor(
                            "a-completely-different-secret-key-of-32+-bytes".getBytes(StandardCharsets.UTF_8)))
                    .compact();

            mockMvc.perform(request(method, path)
                            .header("Authorization", "Bearer " + forged)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Games {

        @Test
        void otherUserCannotReadGameById() throws Exception {
            perform(get("/api/v1/games/{id}", aliceGameId), bobAuth).andExpect(status().isNotFound());
        }

        @Test
        void otherUsersGamesAreNotListed() throws Exception {
            MvcResult bobList = perform(get("/api/v1/games"), bobAuth).andExpect(status().isOk()).andReturn();
            assertThat(readIds(bobList, "$[*].id")).doesNotContain(aliceGameId);

            MvcResult aliceList = perform(get("/api/v1/games"), aliceAuth).andExpect(status().isOk()).andReturn();
            assertThat(readIds(aliceList, "$[*].id")).containsExactly(aliceGameId);
        }

        @Test
        void otherUserCannotUpdateGame() throws Exception {
            perform(put("/api/v1/games/{id}", aliceGameId), bobAuth, GAME_JSON.formatted("Hijacked", ""))
                    .andExpect(status().isNotFound());

            perform(get("/api/v1/games/{id}", aliceGameId), aliceAuth)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Alice Game"))
                    .andExpect(jsonPath("$.sagaId").value(aliceSagaId));
        }

        @Test
        void updatingOtherUsersGameIs404EvenWhenNameCollidesWithOwnGame() throws Exception {
            perform(post("/api/v1/games"), bobAuth, GAME_JSON.formatted("Bob Game", ""))
                    .andExpect(status().isCreated());

            // Ownership is checked before the duplicate-name rule, so this can't surface as 409.
            perform(put("/api/v1/games/{id}", aliceGameId), bobAuth, GAME_JSON.formatted("Bob Game", ""))
                    .andExpect(status().isNotFound());
        }

        @Test
        void otherUserCannotDeleteGame() throws Exception {
            perform(delete("/api/v1/games/{id}", aliceGameId), bobAuth).andExpect(status().isNotFound());

            perform(get("/api/v1/games/{id}", aliceGameId), aliceAuth).andExpect(status().isOk());
        }

        @Test
        void gameNamesAreUniquePerUserNotGlobally() throws Exception {
            // Bob can reuse Alice's game name: uniqueness must not leak across tenants as a 409.
            perform(post("/api/v1/games"), bobAuth, GAME_JSON.formatted("Alice Game", ""))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    class Sagas {

        @Test
        void otherUserCannotReadSagaById() throws Exception {
            perform(get("/api/v1/sagas/{id}", aliceSagaId), bobAuth).andExpect(status().isNotFound());
        }

        @Test
        void otherUsersSagasAreNotListed() throws Exception {
            MvcResult bobList = perform(get("/api/v1/sagas"), bobAuth).andExpect(status().isOk()).andReturn();
            assertThat(readIds(bobList, "$[*].id")).doesNotContain(aliceSagaId);
        }

        @Test
        void otherUserCannotUpdateSaga() throws Exception {
            perform(put("/api/v1/sagas/{id}", aliceSagaId), bobAuth, "{\"name\": \"Hijacked\"}")
                    .andExpect(status().isNotFound());

            perform(get("/api/v1/sagas/{id}", aliceSagaId), aliceAuth)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Alice Saga"));
        }

        @Test
        void updatingOtherUsersSagaIs404EvenWhenNameCollidesWithOwnSaga() throws Exception {
            perform(post("/api/v1/sagas"), bobAuth, "{\"name\": \"Bob Saga\"}").andExpect(status().isCreated());

            perform(put("/api/v1/sagas/{id}", aliceSagaId), bobAuth, "{\"name\": \"Bob Saga\"}")
                    .andExpect(status().isNotFound());
        }

        @Test
        void otherUserCannotDeleteSaga() throws Exception {
            perform(delete("/api/v1/sagas/{id}", aliceSagaId), bobAuth).andExpect(status().isNotFound());

            perform(get("/api/v1/sagas/{id}", aliceSagaId), aliceAuth).andExpect(status().isOk());
        }

        @Test
        void otherUserCannotAttachForeignSagaToNewGame() throws Exception {
            perform(post("/api/v1/games"), bobAuth, GAME_JSON.formatted("Bob Game", ", \"sagaId\": " + aliceSagaId))
                    .andExpect(status().isNotFound());

            MvcResult bobList = perform(get("/api/v1/games"), bobAuth).andExpect(status().isOk()).andReturn();
            assertThat(readIds(bobList, "$[*].id")).isEmpty();
        }

        @Test
        void otherUserCannotAttachForeignSagaToOwnGame() throws Exception {
            Long bobGameId = readId(perform(post("/api/v1/games"), bobAuth, GAME_JSON.formatted("Bob Game", ""))
                    .andExpect(status().isCreated()).andReturn());

            perform(put("/api/v1/games/{id}", bobGameId), bobAuth,
                    GAME_JSON.formatted("Bob Game", ", \"sagaId\": " + aliceSagaId))
                    .andExpect(status().isNotFound());

            perform(get("/api/v1/games/{id}", bobGameId), bobAuth)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sagaId").doesNotExist());
        }
    }

    @Nested
    class Experiences {

        @Test
        void otherUserCannotReadExperienceById() throws Exception {
            perform(get("/api/v1/experiences/{id}", aliceExperienceId), bobAuth).andExpect(status().isNotFound());
        }

        @Test
        void otherUserCannotListExperiencesOfForeignGame() throws Exception {
            perform(get("/api/v1/games/{gameId}/experiences", aliceGameId), bobAuth)
                    .andExpect(status().isNotFound());
        }

        @Test
        void otherUserCannotLogExperienceOnForeignGame() throws Exception {
            perform(post("/api/v1/games/{gameId}/experiences", aliceGameId), bobAuth,
                    EXPERIENCE_JSON.formatted("Bob run"))
                    .andExpect(status().isNotFound());

            MvcResult aliceList = perform(get("/api/v1/games/{gameId}/experiences", aliceGameId), aliceAuth)
                    .andExpect(status().isOk()).andReturn();
            assertThat(readIds(aliceList, "$[*].id")).containsExactly(aliceExperienceId);
        }

        @Test
        void otherUserCannotUpdateExperience() throws Exception {
            perform(put("/api/v1/experiences/{id}", aliceExperienceId), bobAuth, EXPERIENCE_JSON.formatted("Hijacked"))
                    .andExpect(status().isNotFound());

            perform(get("/api/v1/experiences/{id}", aliceExperienceId), aliceAuth)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.runLabel").value("Alice run"));
        }

        @Test
        void otherUserCannotDeleteExperience() throws Exception {
            perform(delete("/api/v1/experiences/{id}", aliceExperienceId), bobAuth).andExpect(status().isNotFound());

            perform(get("/api/v1/experiences/{id}", aliceExperienceId), aliceAuth).andExpect(status().isOk());
        }
    }

    @Nested
    class OnlinePlaytime {

        @Test
        void otherUserCannotReadOnlinePlaytimeOfForeignGame() throws Exception {
            perform(get("/api/v1/games/{gameId}/online-playtime", aliceGameId), bobAuth)
                    .andExpect(status().isNotFound());
        }

        @Test
        void otherUserCannotUpsertOnlinePlaytimeOnForeignGame() throws Exception {
            perform(put("/api/v1/games/{gameId}/online-playtime", aliceGameId), bobAuth,
                    ONLINE_PLAYTIME_JSON.formatted("1.0"))
                    .andExpect(status().isNotFound());

            perform(get("/api/v1/games/{gameId}/online-playtime", aliceGameId), aliceAuth)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalHours").value(100.0));
        }
    }

    @Nested
    class Stats {

        @Test
        void summaryOnlyCountsOwnData() throws Exception {
            perform(get("/api/v1/stats/summary"), bobAuth)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalGames").value(0))
                    .andExpect(jsonPath("$.totalSagas").value(0))
                    .andExpect(jsonPath("$.totalExperiences").value(0))
                    .andExpect(jsonPath("$.totalSingleplayerHours").value(0.0))
                    .andExpect(jsonPath("$.totalOnlineHours").value(0.0))
                    .andExpect(jsonPath("$.totalPlatinums").value(0));

            perform(get("/api/v1/stats/summary"), aliceAuth)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalGames").value(1))
                    .andExpect(jsonPath("$.totalExperiences").value(1))
                    .andExpect(jsonPath("$.totalOnlineHours").value(100.0));
        }

        @Test
        void breakdownsAreEmptyForUserWithoutData() throws Exception {
            for (String path : new String[]{
                    "/api/v1/stats/by-year",
                    "/api/v1/stats/by-saga",
                    "/api/v1/stats/by-genre",
                    "/api/v1/stats/top-rated",
                    "/api/v1/stats/most-played/singleplayer",
                    "/api/v1/stats/most-played/online",
                    "/api/v1/stats/years",
                    "/api/v1/stats/in-progress"}) {
                perform(get(path), bobAuth)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.length()").value(0));
            }
        }

        @Test
        void rankingsNeverIncludeOtherUsersGames() throws Exception {
            // Give Bob data of his own so the lists are non-empty and actually filtered.
            Long bobGameId = readId(perform(post("/api/v1/games"), bobAuth, GAME_JSON.formatted("Bob Game", ""))
                    .andExpect(status().isCreated()).andReturn());
            perform(post("/api/v1/games/{gameId}/experiences", bobGameId), bobAuth, EXPERIENCE_JSON.formatted("Bob run"))
                    .andExpect(status().isCreated());
            perform(put("/api/v1/games/{gameId}/online-playtime", bobGameId), bobAuth, ONLINE_PLAYTIME_JSON.formatted("5.0"))
                    .andExpect(status().isOk());

            for (String path : new String[]{
                    "/api/v1/stats/top-rated",
                    "/api/v1/stats/most-played/singleplayer",
                    "/api/v1/stats/most-played/online"}) {
                MvcResult result = perform(get(path), bobAuth).andExpect(status().isOk()).andReturn();
                assertThat(readIds(result, "$[*].gameId")).as(path).containsExactly(bobGameId);
            }

            MvcResult bySaga = perform(get("/api/v1/stats/by-saga"), bobAuth).andExpect(status().isOk()).andReturn();
            assertThat(readIds(bySaga, "$[*].sagaId")).doesNotContain(aliceSagaId);
        }

        @Test
        void yearSummaryOnlyUsesOwnData() throws Exception {
            // Alice's experience is a COMPLETADO run of 2024 rated 9.
            perform(get("/api/v1/stats/years/2024"), bobAuth)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.experienceCount").value(0))
                    .andExpect(jsonPath("$.totalHours").value(0.0))
                    .andExpect(jsonPath("$.experiences.length()").value(0))
                    .andExpect(jsonPath("$.mostPlayed.length()").value(0));

            perform(get("/api/v1/stats/years/2024"), aliceAuth)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.experienceCount").value(1))
                    .andExpect(jsonPath("$.experiences[0].experienceId").value(aliceExperienceId));
        }

        @Test
        void inProgressNeverListsOtherUsersRuns() throws Exception {
            perform(post("/api/v1/games/{gameId}/experiences", aliceGameId), aliceAuth,
                    "{\"runLabel\": \"Alice live\", \"status\": \"EN_CURSO\", \"platform\": \"PC\"}")
                    .andExpect(status().isCreated());

            perform(get("/api/v1/stats/in-progress"), bobAuth)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
            perform(get("/api/v1/stats/in-progress"), aliceAuth)
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        void yearNotesArePerUser() throws Exception {
            perform(put("/api/v1/stats/years/2024/note"), aliceAuth, "{\"summary\": \"Alice note\"}")
                    .andExpect(status().isOk());

            perform(get("/api/v1/stats/years/2024"), bobAuth)
                    .andExpect(jsonPath("$.note.summary").doesNotExist());

            perform(put("/api/v1/stats/years/2024/note"), bobAuth, "{\"summary\": \"Bob note\"}")
                    .andExpect(status().isOk());

            perform(get("/api/v1/stats/years/2024"), aliceAuth)
                    .andExpect(jsonPath("$.note.summary").value("Alice note"));
        }
    }

    @Nested
    class CurrentUser {

        @Test
        void meReturnsOnlyTheCallersProfile() throws Exception {
            perform(get("/api/v1/users/me"), bobAuth)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(bob.getEmail()))
                    .andExpect(jsonPath("$.username").value(bob.getUsername()));
        }

        @Test
        void renamingOnlyAffectsTheCaller() throws Exception {
            String newName = "renamed" + bob.getId();
            perform(patch("/api/v1/users/me"), bobAuth, "{\"username\": \"" + newName + "\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(bob.getEmail()));

            perform(get("/api/v1/users/me"), aliceAuth)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(alice.getUsername()))
                    .andExpect(jsonPath("$.email").value(alice.getEmail()));
        }
    }

    @Nested
    class Account {

        @Test
        void exportNeverIncludesOtherUsersData() throws Exception {
            for (String format : new String[]{"csv", "markdown"}) {
                MvcResult result = perform(get("/api/v1/users/me/export").param("format", format), bobAuth)
                        .andExpect(status().isOk()).andReturn();
                String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
                assertThat(body).as(format).doesNotContain("Alice Game", "Alice run", "Alice Saga");
            }
        }

        @Test
        void deletingAnAccountLeavesOtherUsersUntouched() throws Exception {
            perform(delete("/api/v1/users/me"), bobAuth, "{\"confirm\": \"" + bob.getUsername() + "\"}")
                    .andExpect(status().isNoContent());

            perform(get("/api/v1/games/{id}", aliceGameId), aliceAuth).andExpect(status().isOk());
            perform(get("/api/v1/experiences/{id}", aliceExperienceId), aliceAuth).andExpect(status().isOk());
            perform(get("/api/v1/sagas/{id}", aliceSagaId), aliceAuth).andExpect(status().isOk());
            perform(get("/api/v1/users/me"), aliceAuth).andExpect(status().isOk());
        }

        @Test
        void confirmingWithAnotherUsersNameDeletesNothing() throws Exception {
            perform(delete("/api/v1/users/me"), bobAuth, "{\"confirm\": \"" + alice.getUsername() + "\"}")
                    .andExpect(status().isBadRequest());

            assertThat(userRepository.findById(alice.getId())).isPresent();
            assertThat(userRepository.findById(bob.getId())).isPresent();
        }
    }

    @Nested
    class SteamIntegration {

        private Long alicePendingGameId;

        @BeforeEach
        void linkAliceSteamAccount() {
            // Mirrors what SteamSyncServiceImpl.sync persists, without calling the real Steam API.
            steamLinkRepository.save(SteamLink.builder()
                    .userId(alice.getId())
                    // steam_id is unique (V13): each test's fresh Alice needs her own Steam account.
                    .steamId("7656119" + (10_000_000_000L + alice.getId()))
                    .personaName("alice-steam")
                    .linkedAt(LocalDateTime.now())
                    .build());

            Game pending = gameRepository.save(Game.builder()
                    .userId(alice.getId())
                    .name("Alice Steam Game")
                    .reviewStatus(ReviewStatus.PENDING_REVIEW)
                    .genres(new HashSet<>())
                    .build());
            alicePendingGameId = pending.getId();

            gamePlatformLinkRepository.save(GamePlatformLink.builder()
                    .userId(alice.getId())
                    .game(pending)
                    .platform(ExternalPlatform.STEAM)
                    .externalId("620")
                    .lastSyncedPlaytimeMinutes(600L)
                    .lastSyncedAt(LocalDateTime.now())
                    .build());
        }

        @Test
        void otherUsersPendingGamesAreNotListed() throws Exception {
            MvcResult bobPending = perform(get("/api/v1/integrations/steam/pending"), bobAuth)
                    .andExpect(status().isOk()).andReturn();
            assertThat(readIds(bobPending, "$[*].id")).doesNotContain(alicePendingGameId);

            MvcResult alicePending = perform(get("/api/v1/integrations/steam/pending"), aliceAuth)
                    .andExpect(status().isOk()).andReturn();
            assertThat(readIds(alicePending, "$[*].id")).containsExactly(alicePendingGameId);
        }

        @Test
        void otherUserCannotConfirmForeignPendingGame() throws Exception {
            perform(put("/api/v1/integrations/steam/pending/{gameId}/confirm", alicePendingGameId), bobAuth,
                    GAME_JSON.formatted("Hijacked", ""))
                    .andExpect(status().isNotFound());

            perform(get("/api/v1/games/{id}", alicePendingGameId), aliceAuth)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Alice Steam Game"))
                    .andExpect(jsonPath("$.reviewStatus").value("PENDING_REVIEW"));
        }

        @Test
        void otherUserCannotSyncThroughForeignSteamLink() throws Exception {
            // Bob has no link of his own; Alice's link must not be picked up for him.
            perform(post("/api/v1/integrations/steam/sync"), bobAuth).andExpect(status().isNotFound());

            assertThat(steamLinkRepository.findByUserId(bob.getId())).isEmpty();
            assertThat(steamLinkRepository.findByUserId(alice.getId())).isPresent();
        }
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder,
                                  String authorization) throws Exception {
        return mockMvc.perform(builder.header("Authorization", authorization));
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder,
                                  String authorization, String jsonBody) throws Exception {
        return mockMvc.perform(builder
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody));
    }
}
