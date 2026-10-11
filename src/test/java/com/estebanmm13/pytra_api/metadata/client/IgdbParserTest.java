package com.estebanmm13.pytra_api.metadata.client;

import com.estebanmm13.pytra_api.error.IgdbIntegrationException;
import com.estebanmm13.pytra_api.metadata.dto.IgdbGameMetadataDto;
import com.estebanmm13.pytra_api.metadata.dto.IgdbSearchResultDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fixtures under /igdb follow the shape of IGDB v4 {@code /games} responses for the fields we request. */
class IgdbParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void searchKeepsGamesAndDlcButDropsBundlesAndMods() {
        List<IgdbSearchResultDto> results = IgdbParser.parseSearch(fixture("search-god-of-war.json"), 10);

        assertThat(results).extracting(IgdbSearchResultDto::igdbId).containsExactly(112875L, 284715L, 549L, 999003L);
        IgdbSearchResultDto ragnarok = results.getFirst();
        assertThat(ragnarok.name()).isEqualTo("God of War Ragnarök");
        assertThat(ragnarok.year()).isEqualTo(2022);
        assertThat(ragnarok.imageUrl()).isEqualTo("https://images.igdb.com/igdb/image/upload/t_cover_small/co4ocm.jpg");
        assertThat(ragnarok.platforms()).containsExactly("PC", "PS4", "PS5");
    }

    @Test
    void searchToleratesMissingCoverDateAndAbbreviation() {
        IgdbSearchResultDto unknown = IgdbParser.parseSearch(fixture("search-god-of-war.json"), 10).getLast();

        assertThat(unknown.year()).isNull();
        assertThat(unknown.imageUrl()).isNull();
        assertThat(unknown.platforms()).containsExactly("Mystery Console");
    }

    @Test
    void searchStopsAtTheLimitAndSkipsMalformedRows() {
        assertThat(IgdbParser.parseSearch(fixture("search-god-of-war.json"), 2)).hasSize(2);
        assertThat(IgdbParser.parseSearch(json("""
                [{"name":"No id"},{"id":5},{"id":6,"name":"   "},{"id":7,"name":"Ok"}]"""), 10))
                .extracting(IgdbSearchResultDto::igdbId).containsExactly(7L);
        assertThat(IgdbParser.parseSearch(json("{\"message\":\"oops\"}"), 10)).isEmpty();
    }

    @Test
    void normalizesDetails() {
        IgdbGameMetadataDto dto = IgdbParser.parseDetails(fixture("game-valhalla.json"), 284715);

        assertThat(dto.igdbId()).isEqualTo(284715);
        assertThat(dto.name()).isEqualTo("God of War Ragnarök: Valhalla");
        assertThat(dto.developer()).isEqualTo("Santa Monica Studio");
        assertThat(dto.publisher()).isEqualTo("Sony Interactive Entertainment");
        assertThat(dto.releaseDate()).isEqualTo(LocalDate.of(2023, 12, 12));
        assertThat(dto.genres()).containsExactly("Role-playing (RPG)", "Hack and slash/Beat 'em up", "Adventure");
        assertThat(dto.platforms()).containsExactly("PlayStation 4", "PlayStation 5");
        // Landscape artwork preferred over the portrait cover for Pytra's banner-shaped covers.
        assertThat(dto.coverImageUrl()).isEqualTo("https://images.igdb.com/igdb/image/upload/t_screenshot_big/ar2x9z.jpg");
    }

    @Test
    void fallsBackToThePortraitCoverWithoutArtworks() {
        IgdbGameMetadataDto dto = IgdbParser.parseDetails(json("""
                [{"id":1,"name":"Knack","cover":{"image_id":"co1abc"},"artworks":[{"image_id":"../evil"}]}]"""), 1);

        assertThat(dto.coverImageUrl()).isEqualTo("https://images.igdb.com/igdb/image/upload/t_cover_big/co1abc.jpg");
    }

    @Test
    void dropsUnsafeImageIdsAndMissingOptionalFields() {
        IgdbGameMetadataDto dto = IgdbParser.parseDetails(json("""
                [{"id":1,"name":"  Saros  ","cover":{"image_id":"x/../../y"},
                  "involved_companies":[{"company":{"name":"Nobody"}}]}]"""), 1);

        assertThat(dto.name()).isEqualTo("Saros");
        assertThat(dto.coverImageUrl()).isNull();
        assertThat(dto.developer()).isNull();
        assertThat(dto.publisher()).isNull();
        assertThat(dto.releaseDate()).isNull();
        assertThat(dto.genres()).isEmpty();
        assertThat(dto.platforms()).isEmpty();
    }

    @Test
    void futureReleaseDatesAreDropped() {
        long nextYear = LocalDate.now(ZoneOffset.UTC).plusYears(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        IgdbGameMetadataDto dto = IgdbParser.parseDetails(json("""
                [{"id":1,"name":"Grand Theft Auto VI","first_release_date":%d}]""".formatted(nextYear)), 1);

        assertThat(dto.releaseDate()).isNull();
    }

    @Test
    void emptyOrForeignResultMeansGameNotFound() {
        for (String body : List.of("[]", "{}", "[{\"id\":2,\"name\":\"Other\"}]", "[{\"id\":1}]")) {
            assertThatThrownBy(() -> IgdbParser.parseDetails(json(body), 1))
                    .isInstanceOfSatisfying(IgdbIntegrationException.class,
                            e -> assertThat(e.getCode()).isEqualTo(IgdbIntegrationException.GAME_NOT_FOUND));
        }
    }

    private static JsonNode fixture(String name) {
        try (InputStream in = IgdbParserTest.class.getResourceAsStream("/igdb/" + name)) {
            return MAPPER.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode json(String raw) {
        try {
            return MAPPER.readTree(raw);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
