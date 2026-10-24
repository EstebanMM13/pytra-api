package com.estebanmm13.pytra_api.obsidian;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import com.estebanmm13.pytra_api.auth.model.PasswordResetToken;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.auth.repository.PasswordResetTokenRepository;
import com.estebanmm13.pytra_api.auth.security.TokenGenerator;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Personal access tokens (managed with the session JWT) and the Obsidian export they unlock. */
class ObsidianExportIntegrationTests extends AbstractIntegrationTest {

    private static final String EXPORT = "/api/v1/export/obsidian";
    private static final String TOKENS = "/api/v1/users/me/tokens";

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private TokenGenerator tokenGenerator;

    private User alice;
    private User bob;
    private String aliceAuth;
    private String bobAuth;

    @BeforeEach
    void createUsers() {
        alice = createVerifiedUser("obalice");
        bob = createVerifiedUser("obbob");
        aliceAuth = bearer(alice);
        bobAuth = bearer(bob);
    }

    private String createToken(String authorization, String name) throws Exception {
        MvcResult result = performAs(authorization, post(TOKENS), "{\"name\": \"" + name + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.token", matchesPattern("pytra_pat_[A-Za-z0-9_-]{43}")))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    @Test
    void tokenIsShownOnceAndListedOnlyByPrefix() throws Exception {
        String token = createToken(aliceAuth, "Obsidian");

        MvcResult list = performAs(aliceAuth, get(TOKENS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Obsidian"))
                .andExpect(jsonPath("$[0].prefix").value(token.substring(0, 12)))
                .andExpect(jsonPath("$[0].lastUsedAt").doesNotExist())
                .andReturn();
        assertThat(list.getResponse().getContentAsString()).doesNotContain(token.substring(12));

        String stored = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM personal_access_tokens WHERE user_id = ?", String.class, alice.getId());
        assertThat(stored).hasSize(64).doesNotContain(token.substring(10));

        // Bob sees none of Alice's tokens.
        performAs(bobAuth, get(TOKENS)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void tokenOnlyWorksOnTheExport() throws Exception {
        String token = "Bearer " + createToken(aliceAuth, "Obsidian");

        performAs(token, get(EXPORT)).andExpect(status().isOk()).andExpect(jsonPath("$.schemaVersion").value(1));

        performAs(token, get("/api/v1/games")).andExpect(status().isUnauthorized());
        performAs(token, get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        performAs(token, get("/api/v1/users/me/export")).andExpect(status().isUnauthorized());
        performAs(token, get(TOKENS)).andExpect(status().isUnauthorized());
        performAs(token, post(TOKENS), "{\"name\": \"Escalation\"}").andExpect(status().isUnauthorized());
        performAs(token, post("/api/v1/games"), "{\"name\": \"X\", \"category\": \"SINGLEPLAYER\"}")
                .andExpect(status().isUnauthorized());
        performAs(token, patch("/api/v1/users/me"), "{\"avatar\": null}").andExpect(status().isUnauthorized());
        performAs(token, delete("/api/v1/users/me"), "{\"confirm\": \"" + alice.getUsername() + "\"}")
                .andExpect(status().isUnauthorized());
        assertThat(userRepository.findById(alice.getId())).isPresent();

        // Using the token stamped its last use.
        performAs(aliceAuth, get(TOKENS)).andExpect(jsonPath("$[0].lastUsedAt").exists());
    }

    @Test
    void unknownAndRevokedTokensAre401() throws Exception {
        performAs("Bearer pytra_pat_" + "x".repeat(43), get(EXPORT)).andExpect(status().isUnauthorized());

        String token = createToken(aliceAuth, "Obsidian");
        Long id = ((Number) JsonPath.read(performAs(aliceAuth, get(TOKENS)).andReturn()
                .getResponse().getContentAsString(), "$[0].id")).longValue();

        // Bob cannot revoke it.
        performAs(bobAuth, delete(TOKENS + "/{id}", id)).andExpect(status().isNotFound());
        performAs("Bearer " + token, get(EXPORT)).andExpect(status().isOk());

        performAs(aliceAuth, delete(TOKENS + "/{id}", id)).andExpect(status().isNoContent());
        performAs("Bearer " + token, get(EXPORT)).andExpect(status().isUnauthorized());
        performAs(aliceAuth, get(TOKENS)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void atMostFiveTokensPerUser() throws Exception {
        for (int i = 1; i <= 5; i++) {
            createToken(aliceAuth, "Token " + i);
        }
        performAs(aliceAuth, post(TOKENS), "{\"name\": \"Sixth\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("ACCESS_TOKEN_LIMIT"));
        // Another user's quota is separate.
        createToken(bobAuth, "Bob token");

        performAs(aliceAuth, post(TOKENS), "{\"name\": \"   \"}").andExpect(status().isBadRequest());
        performAs(aliceAuth, post(TOKENS), "{\"name\": \"" + "n".repeat(51) + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportHasOnlyTheOwnersConfirmedLibrary() throws Exception {
        Long sagaId = readId(performAs(aliceAuth, post("/api/v1/sagas"), "{\"name\": \"Obsidian Saga\"}")
                .andExpect(status().isCreated()).andReturn());
        Long gameId = createGameVia(aliceAuth, "Obsidian Game", ", \"sagaId\": " + sagaId);
        Long runId = createExperienceVia(aliceAuth, gameId,
                run("Run 1", "COMPLETADO", 9.5, 14.5, 2025, "2025-11-14", "2025-11-20", ""));
        Game pending = gameRepository.save(Game.builder().userId(alice.getId()).name("Pending Import")
                .reviewStatus(ReviewStatus.PENDING_REVIEW).updatedAt(LocalDateTime.now()).build());
        Long bobGame = createGameVia(bobAuth, "Bob Secret Game", "");

        String token = "Bearer " + createToken(aliceAuth, "Obsidian");
        MvcResult result = performAs(token, get(EXPORT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverTime").exists())
                .andExpect(jsonPath("$.sagas", hasSize(1)))
                .andExpect(jsonPath("$.sagas[0].name").value("Obsidian Saga"))
                .andExpect(jsonPath("$.games", hasSize(1)))
                .andExpect(jsonPath("$.games[0].name").value("Obsidian Game"))
                .andExpect(jsonPath("$.games[0].category").value("SINGLEPLAYER"))
                .andExpect(jsonPath("$.games[0].online").isEmpty())
                .andExpect(jsonPath("$.experiences", hasSize(1)))
                .andExpect(jsonPath("$.experiences[0].runLabel").value("Run 1"))
                .andExpect(jsonPath("$.experiences[0].year").value(2025))
                .andExpect(jsonPath("$.experiences[0].rating").value(9.5))
                .andExpect(jsonPath("$.experiences[0].hours").value(14.5))
                .andExpect(jsonPath("$.experiences[0].startDate").value("2025-11-14"))
                .andExpect(jsonPath("$.experiences[0].platform").value("PC"))
                .andReturn();
        assertThat(readIds(result, "$.games[*].id")).containsExactly(gameId).doesNotContain(pending.getId(), bobGame);
        assertThat(readIds(result, "$.games[*].sagaId")).containsExactly(sagaId);
        assertThat(readIds(result, "$.experiences[*].id")).containsExactly(runId);
        assertThat(readIds(result, "$.experiences[*].gameId")).containsExactly(gameId);

        // The session JWT works on the export too, with the same snapshot.
        performAs(aliceAuth, get(EXPORT)).andExpect(status().isOk()).andExpect(jsonPath("$.games", hasSize(1)));
        performAs(bobAuth, get(EXPORT)).andExpect(status().isOk())
                .andExpect(jsonPath("$.games", hasSize(1)))
                .andExpect(jsonPath("$.games[0].name").value("Bob Secret Game"));
    }

    @Test
    void resettingThePasswordRevokesEveryToken() throws Exception {
        String aliceToken = "Bearer " + createToken(aliceAuth, "Obsidian");
        String bobToken = "Bearer " + createToken(bobAuth, "Obsidian");
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .user(alice).tokenHash(tokenGenerator.hashToken("obsidian-reset-" + alice.getId()))
                .expiresAt(LocalDateTime.now().plusHours(1)).build());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"obsidian-reset-" + alice.getId() + "\", \"newPassword\": \"Brand-new-pass-123\"}"))
                .andExpect(status().is2xxSuccessful());

        performAs(aliceToken, get(EXPORT)).andExpect(status().isUnauthorized());
        performAs(aliceAuth, get(TOKENS)).andExpect(jsonPath("$", hasSize(0)));
        // Other users keep theirs.
        performAs(bobToken, get(EXPORT)).andExpect(status().isOk());
    }

    @Test
    void deletingTheAccountDeletesItsTokens() throws Exception {
        String token = "Bearer " + createToken(aliceAuth, "Obsidian");
        performAs(aliceAuth, delete("/api/v1/users/me"), "{\"confirm\": \"" + alice.getUsername() + "\"}")
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM personal_access_tokens WHERE user_id = ?", Long.class, alice.getId())).isZero();
        performAs(token, get(EXPORT)).andExpect(status().isUnauthorized());
    }
}
