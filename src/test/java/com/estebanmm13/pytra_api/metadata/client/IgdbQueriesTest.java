package com.estebanmm13.pytra_api.metadata.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IgdbQueriesTest {

    @Test
    void searchQuotesTheTermAndExcludesEditions() {
        assertThat(IgdbQueries.search("zelda", 20)).isEqualTo("search \"zelda\"; fields "
                + "name,first_release_date,cover.image_id,platforms.name,platforms.abbreviation,game_type"
                + "; where version_parent = null; limit 20;");
    }

    @Test
    void termCannotEscapeTheStringLiteral() {
        String query = IgdbQueries.search("x\"; fields *; where id = 1; \\", 10);

        assertThat(query).startsWith("search \"x\\\"; fields *; where id = 1; \\\\\"; fields name,");
    }

    @Test
    void controlCharactersBecomeSpaces() {
        assertThat(IgdbQueries.escape("a\nb\tc\u0000")).isEqualTo("a b c ");
        assertThat(IgdbQueries.escape("Pokémon: Añil")).isEqualTo("Pokémon: Añil");
    }

    @Test
    void detailsAskForOneGameById() {
        assertThat(IgdbQueries.details(284715)).endsWith("; where id = 284715; limit 1;")
                .contains("artworks.image_id", "involved_companies.developer", "genres.name");
    }
}
