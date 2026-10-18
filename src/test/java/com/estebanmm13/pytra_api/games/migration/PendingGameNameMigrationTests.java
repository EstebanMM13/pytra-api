package com.estebanmm13.pytra_api.games.migration;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs V27 on Postgres against sample pending games. Flyway already applied it to the empty test database,
 * so the script is executed again here (twice, to show it is idempotent).
 */
class PendingGameNameMigrationTests extends AbstractIntegrationTest {

    private static final String MIGRATION = "db/migration/V27__clean_pending_game_names.sql";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void cleansPendingNamesLikeTheImportersDo() throws Exception {
        Long userId = createVerifiedUser("cleannames").getId();
        Map<String, String> expectedByName = Map.of(
                "Ghost of Tsushima™: Director’s Cut", "Ghost of Tsushima: Director's Cut",
                "Grand Theft Auto V (PlayStation®5)", "Grand Theft Auto V",
                "Hogwarts Legacy (PS4)", "Hogwarts Legacy",
                "Ratchet & Clank PS4 & PS5", "Ratchet & Clank",
                "Uncharted™, Legacy", "Uncharted, Legacy",
                "Foo™Bar", "Foo Bar",
                "Tom Clancy’s Rainbow Six®  Siege", "Tom Clancy's Rainbow Six Siege");
        Map<String, Long> idByName = new HashMap<>();
        expectedByName.keySet().forEach(name -> idByName.put(name, insertGame(userId, name, "PENDING_REVIEW", name)));

        runMigrationTwice();

        expectedByName.forEach((name, expected) -> {
            Map<String, Object> row = row(idByName.get(name));
            assertThat(row.get("name")).isEqualTo(expected);
            assertThat(row.get("imported_name")).isEqualTo(expected);
            assertThat(row.get("igdb_lookup_at")).isNull();
        });
    }

    @Test
    void nullImportedNameBecomesTheCleanNameButAnEditedOneIsKept() throws Exception {
        Long userId = createVerifiedUser("cleanimported").getId();
        Long imported = insertGame(userId, "Spider-Man™", "PENDING_REVIEW", null);
        Long edited = insertGame(userId, "Bloodborne™", "PENDING_REVIEW", "Something else");

        runMigrationTwice();

        assertThat(row(imported)).containsEntry("name", "Spider-Man").containsEntry("imported_name", "Spider-Man");
        assertThat(row(imported).get("igdb_lookup_at")).isNull();
        assertThat(row(edited)).containsEntry("name", "Bloodborne").containsEntry("imported_name", "Something else");
        assertThat(row(edited).get("igdb_lookup_at")).isNotNull();
    }

    @Test
    void neverCollidesAndNeverTouchesConfirmedOrIgnoredGames() throws Exception {
        Long userId = createVerifiedUser("cleancollide").getId();
        Long otherUserId = createVerifiedUser("cleanother").getId();
        insertGame(userId, "halo", "CONFIRMED", null);
        Long collidesWithConfirmed = insertGame(userId, "Halo®", "PENDING_REVIEW", "Halo®");
        Long firstDoom = insertGame(userId, "Doom™", "PENDING_REVIEW", "Doom™");
        Long secondDoom = insertGame(userId, "DOOM®", "PENDING_REVIEW", "DOOM®");
        Long otherUsersDoom = insertGame(otherUserId, "Doom™", "PENDING_REVIEW", "Doom™");
        Long onlyMarks = insertGame(userId, "™", "PENDING_REVIEW", "™");
        Long confirmed = insertGame(userId, "Dreams™", "CONFIRMED", "Dreams™");
        Long ignored = insertGame(userId, "Astro Bot™", "IGNORED", "Astro Bot™");

        runMigrationTwice();

        assertThat(row(collidesWithConfirmed)).containsEntry("name", "Halo®");
        assertThat(row(firstDoom)).containsEntry("name", "Doom");
        assertThat(row(secondDoom)).containsEntry("name", "DOOM®");
        assertThat(row(otherUsersDoom)).containsEntry("name", "Doom");
        assertThat(row(onlyMarks)).containsEntry("name", "™");
        assertThat(row(confirmed)).containsEntry("name", "Dreams™");
        assertThat(row(ignored)).containsEntry("name", "Astro Bot™");
    }

    private Long insertGame(Long userId, String name, String reviewStatus, String importedName) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO games (user_id, name, review_status, imported_name, igdb_lookup_at) "
                        + "VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class, userId, name, reviewStatus, importedName, Timestamp.valueOf(LocalDateTime.now()));
    }

    private Map<String, Object> row(Long id) {
        return jdbcTemplate.queryForMap("SELECT name, imported_name, igdb_lookup_at FROM games WHERE id = ?", id);
    }

    private void runMigrationTwice() throws IOException {
        String migration = new ClassPathResource(MIGRATION).getContentAsString(StandardCharsets.UTF_8);
        jdbcTemplate.execute(migration);
        jdbcTemplate.execute(migration);
    }
}
