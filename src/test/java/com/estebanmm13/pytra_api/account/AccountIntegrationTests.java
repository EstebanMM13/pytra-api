package com.estebanmm13.pytra_api.account;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import com.estebanmm13.pytra_api.auth.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Profile createdAt and avatar, data export and account deletion. */
class AccountIntegrationTests extends AbstractIntegrationTest {

    /** Every table holding rows owned by a user (all reference users(id) ON DELETE CASCADE). */
    private static final List<String> USER_OWNED_TABLES = List.of(
            "email_verification_tokens", "password_reset_tokens", "exchange_code_tokens",
            "sagas", "games", "experiences", "online_playtimes",
            "game_platform_links", "steam_links", "steam_link_states", "steam_ignored_apps",
            "year_notes");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${JWT_SECRET}")
    private String jwtSecret;

    private User alice;
    private User bob;
    private String aliceAuth;
    private String bobAuth;

    @BeforeEach
    void createUsers() {
        alice = createVerifiedUser("acalice");
        bob = createVerifiedUser("acbob");
        aliceAuth = bearer(alice);
        bobAuth = bearer(bob);
    }

    @Test
    void meIncludesCreatedAt() throws Exception {
        performAs(aliceAuth, get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.createdAt", containsString(String.valueOf(LocalDateTime.now().getYear()))));
    }

    @Test
    void avatarIsNullUntilPickedAndCanBeChangedAndCleared() throws Exception {
        performAs(aliceAuth, get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar").value(nullValue()));

        performAs(aliceAuth, patch("/api/v1/users/me"), "{\"avatar\": \"ghost\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar").value("ghost"))
                .andExpect(jsonPath("$.username").value(alice.getUsername()));
        assertThat(userRepository.findById(alice.getId()).orElseThrow().getAvatar()).isEqualTo("ghost");

        // A username-only update leaves the avatar alone.
        String newName = "avrenamed" + alice.getId();
        performAs(aliceAuth, patch("/api/v1/users/me"), "{\"username\": \"" + newName + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(newName))
                .andExpect(jsonPath("$.avatar").value("ghost"));

        performAs(aliceAuth, patch("/api/v1/users/me"), "{\"avatar\": null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar").value(nullValue()))
                .andExpect(jsonPath("$.username").value(newName));
        assertThat(userRepository.findById(alice.getId()).orElseThrow().getAvatar()).isNull();
    }

    @Test
    void unknownAvatarIs400AndChangesNothing() throws Exception {
        performAs(aliceAuth, patch("/api/v1/users/me"), "{\"avatar\": \"crown\"}")
                .andExpect(status().isOk());

        for (String invalid : List.of("dragon", "", "CROWN", "x".repeat(40))) {
            performAs(aliceAuth, patch("/api/v1/users/me"), "{\"avatar\": \"" + invalid + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("INVALID_AVATAR"));
        }
        assertThat(userRepository.findById(alice.getId()).orElseThrow().getAvatar()).isEqualTo("crown");

        // Another user's avatar is untouched.
        assertThat(userRepository.findById(bob.getId()).orElseThrow().getAvatar()).isNull();
    }

    @Test
    void csvExportContainsOnlyOwnGamesAndExperiences() throws Exception {
        Long aliceGame = createGameVia(aliceAuth, "Alice Export Game", "");
        createExperienceVia(aliceAuth, aliceGame, run("Alice run", "COMPLETADO", 9, 12.5, 2024, null, null,
                ", \"summary\": \"Great, really\""));
        createGameVia(aliceAuth, "Alice Backlog", "");
        Long bobGame = createGameVia(bobAuth, "Bob Secret Game", "");
        createExperienceVia(bobAuth, bobGame, run("Bob run", "COMPLETADO", 3, 1.0, 2024, null, null, ""));

        MvcResult result = performAs(aliceAuth, get("/api/v1/users/me/export").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("pytra-export-" + alice.getUsername())))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(".csv")))
                .andReturn();

        String csv = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<String> lines = csv.lines().toList();
        assertThat(lines.getFirst()).startsWith("﻿gameName,");
        assertThat(lines).hasSize(3);
        assertThat(csv).contains("Alice Export Game,", "Alice run,2024,COMPLETADO,9,12.5", "\"Great, really\"",
                "Alice Backlog,");
        assertThat(csv).doesNotContain("Bob Secret Game", "Bob run");
    }

    @Test
    void markdownExportHasGameSectionsRunsAndYearNotes() throws Exception {
        Long aliceGame = createGameVia(aliceAuth, "Alice Markdown Game", "");
        createExperienceVia(aliceAuth, aliceGame, run("Platinum run", "COMPLETADO", 10, 80.0, 2024, null, null,
                ", \"pros\": \"Bosses\", \"cons\": \"Camera\""));
        performAs(aliceAuth, put("/api/v1/stats/years/2024/note"), "{\"summary\": \"Alice year\"}")
                .andExpect(status().isOk());
        createGameVia(bobAuth, "Bob Markdown Game", "");
        performAs(bobAuth, put("/api/v1/stats/years/2024/note"), "{\"summary\": \"Bob year\"}")
                .andExpect(status().isOk());

        MvcResult result = performAs(aliceAuth, get("/api/v1/users/me/export").param("format", "markdown"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/markdown")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(".md")))
                .andReturn();

        String md = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(md).contains("## Alice Markdown Game", "### Platinum run", "- **Rating:** 10/10",
                "- **Hours:** 80", "**Pros**\n\nBosses", "**Cons**\n\nCamera", "# Year notes", "## 2024", "Alice year");
        assertThat(md).doesNotContain("Bob Markdown Game", "Bob year");
    }

    @Test
    void unknownExportFormatIs400() throws Exception {
        performAs(aliceAuth, get("/api/v1/users/me/export").param("format", "pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_EXPORT_FORMAT"));
    }

    @Test
    void deleteRequiresTheUsernameAsConfirmation() throws Exception {
        createGameVia(aliceAuth, "Kept Game", "");

        performAs(aliceAuth, delete("/api/v1/users/me"), "{\"confirm\": \"" + bob.getUsername() + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CONFIRMATION_MISMATCH"));
        performAs(aliceAuth, delete("/api/v1/users/me"), "{}")
                .andExpect(status().isBadRequest());
        performAs(aliceAuth, delete("/api/v1/users/me"))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findById(alice.getId())).isPresent();
        assertThat(countOwnedRows(alice.getId(), "games")).isEqualTo(1);
    }

    @Test
    void deleteRemovesTheAccountAndAllItsDataButNothingElse() throws Exception {
        Long aliceGameId = seedEverything(alice, aliceAuth);
        seedEverything(bob, bobAuth);
        for (String table : USER_OWNED_TABLES) {
            assertThat(countOwnedRows(alice.getId(), table)).as("seeded " + table).isPositive();
        }
        long bobRowsBefore = totalOwnedRows(bob.getId());

        // Case-insensitive, like login.
        performAs(aliceAuth, delete("/api/v1/users/me"), "{\"confirm\": \"" + alice.getUsername().toUpperCase() + "\"}")
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(alice.getId())).isEmpty();
        for (String table : USER_OWNED_TABLES) {
            assertThat(countOwnedRows(alice.getId(), table)).as(table).isZero();
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM game_genres WHERE game_id = ?", Long.class, aliceGameId)).isZero();

        assertThat(userRepository.findById(bob.getId())).isPresent();
        assertThat(totalOwnedRows(bob.getId())).isEqualTo(bobRowsBefore);
        performAs(bobAuth, get("/api/v1/stats/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGames").value(1))
                .andExpect(jsonPath("$.totalExperiences").value(1));
    }

    @Test
    void passwordAccountMustSendTheRightPassword() throws Exception {
        alice.setPasswordHash(passwordEncoder.encode("correct-horse"));
        userRepository.save(alice);
        String confirm = "\"confirm\": \"" + alice.getUsername() + "\"";

        performAs(aliceAuth, delete("/api/v1/users/me"), "{" + confirm + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_PASSWORD"));
        performAs(aliceAuth, delete("/api/v1/users/me"), "{" + confirm + ", \"password\": \"wrong\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_PASSWORD"));
        assertThat(userRepository.findById(alice.getId())).isPresent();

        performAs(aliceAuth, delete("/api/v1/users/me"), "{" + confirm + ", \"password\": \"correct-horse\"}")
                .andExpect(status().isNoContent());
        assertThat(userRepository.findById(alice.getId())).isEmpty();
    }

    @Test
    void passwordAccountIsNotDeletableWithAFreshTokenAlone() throws Exception {
        alice.setPasswordHash(passwordEncoder.encode("correct-horse"));
        userRepository.save(alice);

        performAs(aliceAuth, delete("/api/v1/users/me"), "{\"confirm\": \"" + alice.getUsername() + "\"}")
                .andExpect(status().isBadRequest());
        assertThat(userRepository.findById(alice.getId())).isPresent();
    }

    @Test
    void googleOnlyAccountNeedsARecentLogin() throws Exception {
        String staleAuth = bearerIssuedAt(alice, Instant.now().minus(Duration.ofMinutes(11)));
        String body = "{\"confirm\": \"" + alice.getUsername() + "\"}";

        performAs(staleAuth, delete("/api/v1/users/me"), body)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("REAUTH_REQUIRED"));
        assertThat(userRepository.findById(alice.getId())).isPresent();

        String recentAuth = bearerIssuedAt(alice, Instant.now().minus(Duration.ofMinutes(9)));
        performAs(recentAuth, delete("/api/v1/users/me"), body)
                .andExpect(status().isNoContent());
        assertThat(userRepository.findById(alice.getId())).isEmpty();
    }

    @Test
    void tokenOfADeletedAccountIsRejected() throws Exception {
        performAs(aliceAuth, delete("/api/v1/users/me"), "{\"confirm\": \"" + alice.getUsername() + "\"}")
                .andExpect(status().isNoContent());

        performAs(aliceAuth, get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        performAs(aliceAuth, get("/api/v1/games")).andExpect(status().isUnauthorized());
    }

    @Test
    void exportIsThrottledPerUser() throws Exception {
        for (int i = 0; i < 5; i++) {
            performAs(aliceAuth, get("/api/v1/users/me/export")).andExpect(status().isOk());
        }
        performAs(aliceAuth, get("/api/v1/users/me/export").param("format", "markdown"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.message").value("EXPORT_RATE_LIMITED"));

        performAs(bobAuth, get("/api/v1/users/me/export")).andExpect(status().isOk());
    }

    /** Same claims as the login flow, signed with the test key, but with a chosen {@code iat}. */
    private String bearerIssuedAt(User user, Instant issuedAt) {
        return "Bearer " + Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret)))
                .compact();
    }

    /** One row in every user-owned table, through the API where one exists. Returns the game id. */
    private Long seedEverything(User user, String auth) throws Exception {
        Long sagaId = readId(performAs(auth, post("/api/v1/sagas"), "{\"name\": \"Saga\"}")
                .andExpect(status().isCreated()).andReturn());
        Long genreId = readId(performAs(auth, post("/api/v1/genres"),
                "{\"name\": \"Genre " + UUID.randomUUID() + "\"}").andReturn());
        Long gameId = createGameVia(auth, "Everything", ", \"sagaId\": " + sagaId + ", \"genreIds\": [" + genreId + "]");
        Long experienceId = createExperienceVia(auth, gameId, run("Run", "COMPLETADO", 8, 5.0, 2024, null, null, ""));
        performAs(auth, put("/api/v1/games/{gameId}/online-playtime", gameId), "{\"totalHours\": 4.0}")
                .andExpect(status().isOk());
        performAs(auth, put("/api/v1/stats/years/2024/note"), "{\"summary\": \"Note\"}")
                .andExpect(status().isOk());

        Long userId = user.getId();
        LocalDateTime later = LocalDateTime.now().plusDays(1);
        for (String table : List.of("email_verification_tokens", "password_reset_tokens", "exchange_code_tokens")) {
            jdbcTemplate.update("INSERT INTO " + table + " (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
                    userId, UUID.randomUUID().toString(), later);
        }
        jdbcTemplate.update("INSERT INTO steam_links (user_id, steam_id, persona_name, linked_at) VALUES (?, ?, ?, ?)",
                userId, "7656119" + (20_000_000_000L + userId), "persona", LocalDateTime.now());
        jdbcTemplate.update("INSERT INTO steam_link_states (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
                userId, UUID.randomUUID().toString(), later);
        jdbcTemplate.update("INSERT INTO steam_ignored_apps (user_id, app_id, name, ignored_at) VALUES (?, ?, ?, ?)",
                userId, "620", "Portal 2", LocalDateTime.now());
        jdbcTemplate.update("INSERT INTO game_platform_links (user_id, game_id, platform, external_id, "
                        + "last_synced_playtime_minutes, last_synced_at, experience_id) VALUES (?, ?, 'STEAM', '1', 60, ?, ?)",
                userId, gameId, LocalDateTime.now(), experienceId);
        return gameId;
    }

    private long countOwnedRows(Long userId, String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Long.class, userId);
    }

    private long totalOwnedRows(Long userId) {
        return USER_OWNED_TABLES.stream().mapToLong(table -> countOwnedRows(userId, table)).sum();
    }
}
