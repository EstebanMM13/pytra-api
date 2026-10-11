package com.estebanmm13.pytra_api.games.migration;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs V21 on Postgres against sample covers. Flyway already applied it to the empty test database,
 * so the script is executed again here; that is safe because it is idempotent.
 */
class SteamPortraitCoverMigrationTests extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rewritesSteamHeadersAndLeavesOtherCoversAlone() throws Exception {
        String auth = bearer(createVerifiedUser("covers"));
        Map<Long, String> expectedById = new LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<String, String> sample : SteamPortraitCoverSamples.REWRITTEN.entrySet()) {
            expectedById.put(createGame(auth, "Rewritten " + index++, sample.getKey()), sample.getValue());
        }
        for (String url : SteamPortraitCoverSamples.UNTOUCHED) {
            expectedById.put(createGame(auth, "Untouched " + index++, url), url);
        }

        String migration = migrationSql();
        jdbcTemplate.execute(migration);
        jdbcTemplate.execute(migration);

        expectedById.forEach((id, expected) -> assertThat(
                jdbcTemplate.queryForObject("SELECT cover_image_url FROM games WHERE id = ?", String.class, id))
                .isEqualTo(expected));
    }

    private Long createGame(String auth, String name, String coverUrl) throws Exception {
        return createGameVia(auth, name, ", \"coverImageUrl\": \"" + coverUrl + "\"");
    }

    private static String migrationSql() throws IOException {
        return new ClassPathResource(SteamPortraitCoverSamples.MIGRATION).getContentAsString(StandardCharsets.UTF_8);
    }
}
