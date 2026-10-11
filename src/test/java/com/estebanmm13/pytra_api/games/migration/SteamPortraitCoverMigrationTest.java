package com.estebanmm13.pytra_api.games.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the V21 regexes without a database: the string literals are read from the migration file
 * itself and applied with java.util.regex (the constructs used are plain enough to behave the same
 * as Postgres AREs). {@link SteamPortraitCoverMigrationTests} runs the real SQL on Postgres.
 */
class SteamPortraitCoverMigrationTest {

    private static final Pattern SQL_LITERAL = Pattern.compile("'((?:[^']|'')*)'");

    @Test
    void rewritesSteamHeadersToThePortraitCapsule() throws IOException {
        Migration migration = Migration.load();

        Map<String, String> expected = SteamPortraitCoverSamples.REWRITTEN;
        expected.forEach((before, after) -> assertThat(migration.apply(before)).as(before).isEqualTo(after));
    }

    @Test
    void leavesEveryOtherUrlUntouched() throws IOException {
        Migration migration = Migration.load();

        for (String url : SteamPortraitCoverSamples.UNTOUCHED) {
            assertThat(migration.apply(url)).as(url).isEqualTo(url);
        }
    }

    @Test
    void isIdempotent() throws IOException {
        Migration migration = Migration.load();

        SteamPortraitCoverSamples.REWRITTEN.values()
                .forEach(after -> assertThat(migration.apply(after)).isEqualTo(after));
    }

    /** UPDATE ... SET regexp_replace(url, pattern, replacement, 'i') WHERE url ~* pattern AND url !~ skip. */
    private record Migration(Pattern pattern, String replacement, Pattern where, Pattern skip) {

        static Migration load() throws IOException {
            String sql;
            try (InputStream in = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(SteamPortraitCoverSamples.MIGRATION)) {
                assertThat(in).as(SteamPortraitCoverSamples.MIGRATION).isNotNull();
                sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            String code = sql.lines().filter(line -> !line.strip().startsWith("--")).reduce("", (a, b) -> a + "\n" + b);
            List<String> literals = new ArrayList<>();
            Matcher matcher = SQL_LITERAL.matcher(code);
            while (matcher.find()) literals.add(matcher.group(1).replace("''", "'"));

            // pattern, replacement, flags, where pattern, skip pattern
            assertThat(literals).hasSize(5);
            assertThat(literals.get(2)).isEqualTo("i");
            return new Migration(
                    Pattern.compile(literals.get(0), Pattern.CASE_INSENSITIVE),
                    literals.get(1).replaceAll("\\\\([0-9])", "\\$$1"),
                    Pattern.compile(literals.get(3), Pattern.CASE_INSENSITIVE),
                    Pattern.compile(literals.get(4)));
        }

        String apply(String url) {
            if (!where.matcher(url).find() || skip.matcher(url).find()) return url;
            return pattern.matcher(url).replaceFirst(replacement);
        }
    }
}
