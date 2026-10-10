package com.estebanmm13.pytra_api.metadata.client;

import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import com.estebanmm13.pytra_api.metadata.dto.SteamGameMetadataDto;
import com.estebanmm13.pytra_api.metadata.dto.SteamStoreSearchResultDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fixtures under /steam-store are trimmed copies of real store responses (l=spanish, cc=ES). */
class SteamStoreParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void normalizesSpanishAppDetails() {
        SteamGameMetadataDto dto = SteamStoreParser.parseDetails(fixture("appdetails-elden-ring-spanish.json"), 1245620);

        assertThat(dto.steamAppId()).isEqualTo(1245620);
        assertThat(dto.name()).isEqualTo("ELDEN RING");
        assertThat(dto.developer()).isEqualTo("FromSoftware, Inc.");
        assertThat(dto.publisher()).isEqualTo("FromSoftware, Inc.");
        assertThat(dto.releaseDate()).isEqualTo(LocalDate.of(2022, 2, 24));
        assertThat(dto.genres()).containsExactly("Acción", "Rol");
        assertThat(dto.coverImageUrl())
                .startsWith("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1245620/");
    }

    @Test
    void successFalseMeansAppNotFound() {
        assertThatThrownBy(() -> SteamStoreParser.parseDetails(json("{\"999\":{\"success\":false}}"), 999))
                .isInstanceOfSatisfying(SteamIntegrationException.class,
                        e -> assertThat(e.getCode()).isEqualTo(SteamIntegrationException.APP_NOT_FOUND));
    }

    @Test
    void nullOrForeignBodyMeansAppNotFound() {
        assertThatThrownBy(() -> SteamStoreParser.parseDetails(json("null"), 620))
                .isInstanceOfSatisfying(SteamIntegrationException.class,
                        e -> assertThat(e.getCode()).isEqualTo(SteamIntegrationException.APP_NOT_FOUND));
        assertThatThrownBy(() -> SteamStoreParser.parseDetails(json("{\"440\":{\"success\":true,\"data\":{}}}"), 620))
                .isInstanceOfSatisfying(SteamIntegrationException.class,
                        e -> assertThat(e.getCode()).isEqualTo(SteamIntegrationException.APP_NOT_FOUND));
    }

    @Test
    void dlcIsRejectedAsNotAGame() {
        assertThatThrownBy(() -> SteamStoreParser.parseDetails(fixture("appdetails-dlc.json"), 2778580))
                .isInstanceOfSatisfying(SteamIntegrationException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(SteamIntegrationException.APP_NOT_A_GAME);
                    assertThat(e.getStatus().value()).isEqualTo(422);
                });
    }

    @Test
    void missingOptionalFieldsBecomeNullOrEmpty() {
        SteamGameMetadataDto dto = SteamStoreParser.parseDetails(json("""
                {"10":{"success":true,"data":{"type":"game","name":"  Counter-Strike  ",
                  "developers":[], "publishers":"Valve", "genres":[{"id":"1"},{"description":""}]}}}"""), 10);

        assertThat(dto.name()).isEqualTo("Counter-Strike");
        assertThat(dto.developer()).isNull();
        assertThat(dto.publisher()).isNull();
        assertThat(dto.releaseDate()).isNull();
        assertThat(dto.genres()).isEmpty();
        assertThat(dto.coverImageUrl()).isNull();
    }

    @Test
    void comingSoonAndFutureDatesAreNotReleaseDates() {
        SteamGameMetadataDto comingSoon = SteamStoreParser.parseDetails(json("""
                {"1":{"success":true,"data":{"type":"game","name":"Soon",
                  "release_date":{"coming_soon":true,"date":"1 ENE 2020"}}}}"""), 1);
        int nextYear = LocalDate.now().getYear() + 1;
        SteamGameMetadataDto future = SteamStoreParser.parseDetails(json("""
                {"1":{"success":true,"data":{"type":"game","name":"Later",
                  "release_date":{"coming_soon":false,"date":"1 ENE %d"}}}}""".formatted(nextYear)), 1);

        assertThat(comingSoon.releaseDate()).isNull();
        assertThat(future.releaseDate()).isNull();
    }

    @Test
    void dedupesGenresAndUpgradesHttpCover() {
        SteamGameMetadataDto dto = SteamStoreParser.parseDetails(json("""
                {"1":{"success":true,"data":{"type":"game","name":"Game",
                  "genres":[{"id":"1","description":"Acción"},{"id":"1","description":"Acción"},{"id":"23","description":"Indie"}],
                  "header_image":"http://cdn.akamai.steamstatic.com/steam/apps/1/header.jpg"}}}"""), 1);

        assertThat(dto.genres()).containsExactly("Acción", "Indie");
        assertThat(dto.coverImageUrl()).isEqualTo("https://cdn.akamai.steamstatic.com/steam/apps/1/header.jpg");
    }

    @Test
    void dropsNonHttpCover() {
        SteamGameMetadataDto dto = SteamStoreParser.parseDetails(json("""
                {"1":{"success":true,"data":{"type":"game","name":"Game","header_image":"javascript:alert(1)"}}}"""), 1);

        assertThat(dto.coverImageUrl()).isNull();
    }

    @Test
    void parsesSearchHitsUpToTheLimit() {
        List<SteamStoreSearchResultDto> results = SteamStoreParser.parseSearch(fixture("storesearch-elden.json"), 3);

        assertThat(results).hasSize(3);
        assertThat(results.getFirst().appId()).isEqualTo(1245620);
        assertThat(results.getFirst().name()).isEqualTo("ELDEN RING");
        assertThat(results.getFirst().imageUrl()).startsWith("https://shared.akamai.steamstatic.com/");
    }

    @Test
    void searchSkipsNonAppsAndMalformedItems() {
        List<SteamStoreSearchResultDto> results = SteamStoreParser.parseSearch(json("""
                {"total":4,"items":[
                  {"type":"sub","id":5,"name":"Bundle"},
                  {"type":"app","name":"No id"},
                  {"type":"app","id":7},
                  {"type":"app","id":620,"name":"Portal 2"}
                ]}"""), 10);

        assertThat(results).containsExactly(new SteamStoreSearchResultDto(620, "Portal 2", null));
    }

    @Test
    void searchWithoutItemsIsEmpty() {
        assertThat(SteamStoreParser.parseSearch(json("{\"total\":0}"), 10)).isEmpty();
    }

    private static JsonNode fixture(String name) {
        try (InputStream in = SteamStoreParserTest.class.getResourceAsStream("/steam-store/" + name)) {
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
