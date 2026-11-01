package com.estebanmm13.pytra_api.games.migration;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs V42 on Postgres against sample runs. Flyway already applied it to the empty test database, so the
 * script is executed again here (twice, to show it is idempotent).
 */
class PendingToBacklogMigrationTests extends AbstractIntegrationTest {

    private static final String MIGRATION = "db/migration/V42__move_pending_to_game_backlog.sql";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void gamesWithAPendingRunJoinTheBacklogAndThoseRunsAreDeleted() throws Exception {
        Long userId = createVerifiedUser("backlogmove").getId();
        Long waiting = insertGame(userId, "Waiting");
        Long replayPlanned = insertGame(userId, "Replay planned");
        Long untouched = insertGame(userId, "Untouched");
        Long pendingRun = insertRun(userId, waiting, "Someday", "PENDIENTE");
        Long completed = insertRun(userId, replayPlanned, "First", "COMPLETADO");
        Long pendingReplay = insertRun(userId, replayPlanned, "Second", "PENDIENTE");
        Long playing = insertRun(userId, untouched, "Now", "EN_CURSO");

        runMigrationTwice();

        assertThat(backlog(waiting)).isTrue();
        assertThat(backlog(replayPlanned)).isTrue();
        assertThat(backlog(untouched)).isFalse();
        assertThat(runExists(pendingRun)).isFalse();
        assertThat(runExists(pendingReplay)).isFalse();
        assertThat(runExists(completed)).isTrue();
        assertThat(runExists(playing)).isTrue();
    }

    @Test
    void diaryLinesOfADeletedRunStayDetachedWithoutCountingHours() throws Exception {
        Long userId = createVerifiedUser("backlogdiary").getId();
        Long game = insertGame(userId, "Diary game");
        Long pendingRun = insertRun(userId, game, "Someday", "PENDIENTE");
        Long playing = insertRun(userId, game, "Now", "EN_CURSO");
        Long entry = jdbcTemplate.queryForObject(
                "INSERT INTO diary_entries (user_id, entry_date, created_at, updated_at) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, userId, LocalDate.of(2026, 10, 1), LocalDateTime.now(), LocalDateTime.now());
        Long onPending = insertLine(entry, game, pendingRun);
        Long otherEntry = jdbcTemplate.queryForObject(
                "INSERT INTO diary_entries (user_id, entry_date, created_at, updated_at) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, userId, LocalDate.of(2026, 10, 2), LocalDateTime.now(), LocalDateTime.now());
        Long onPlaying = insertLine(otherEntry, game, playing);

        runMigrationTwice();

        assertThat(line(onPending)).containsEntry("experience_id", null).containsEntry("counts_hours", false);
        assertThat(line(onPlaying)).containsEntry("experience_id", playing).containsEntry("counts_hours", true);
    }

    private Long insertGame(Long userId, String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO games (user_id, name, category, review_status) VALUES (?, ?, 'SINGLEPLAYER', 'CONFIRMED') RETURNING id",
                Long.class, userId, name);
    }

    private Long insertRun(Long userId, Long gameId, String label, String status) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO experiences (user_id, game_id, run_label, status, platform_id) "
                        + "VALUES (?, ?, ?, ?, (SELECT MIN(id) FROM platforms)) RETURNING id",
                Long.class, userId, gameId, label, status);
    }

    private Long insertLine(Long entryId, Long gameId, Long experienceId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO diary_entry_games (entry_id, game_id, experience_id, hours, counts_hours) "
                        + "VALUES (?, ?, ?, 2.00, TRUE) RETURNING id",
                Long.class, entryId, gameId, experienceId);
    }

    private boolean backlog(Long gameId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT backlog FROM games WHERE id = ?", Boolean.class, gameId));
    }

    private boolean runExists(Long runId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM experiences WHERE id = ?", Long.class, runId) == 1;
    }

    private Map<String, Object> line(Long id) {
        return jdbcTemplate.queryForMap("SELECT experience_id, counts_hours FROM diary_entry_games WHERE id = ?", id);
    }

    private void runMigrationTwice() throws IOException {
        String migration = new ClassPathResource(MIGRATION).getContentAsString(StandardCharsets.UTF_8);
        jdbcTemplate.execute(migration);
        jdbcTemplate.execute(migration);
    }
}
