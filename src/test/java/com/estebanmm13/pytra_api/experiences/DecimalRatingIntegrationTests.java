package com.estebanmm13.pytra_api.experiences;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ratings with up to 2 decimals: stored exactly, returned as JSON numbers, never silently rounded. */
class DecimalRatingIntegrationTests extends AbstractIntegrationTest {

    private String auth;
    private Long gameId;

    @BeforeEach
    void setUp() throws Exception {
        auth = bearer(createVerifiedUser("decimal"));
        gameId = createGameVia(auth, "Decimal Game", "");
    }

    @Test
    void decimalRatingIsStoredAndReturnedAsANumber() throws Exception {
        String created = performAs(auth, post("/api/v1/games/{gameId}/experiences", gameId),
                run("Run", "COMPLETADO", new BigDecimal("9.25"), 1.0, null, null, null, ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(9.25))
                .andReturn().getResponse().getContentAsString();
        assertThat(created).contains("\"rating\":9.25");

        // Read back from the database (NUMERIC(4,2)): same value, no trailing zeros.
        String listed = performAs(auth, get("/api/v1/games/{gameId}/experiences", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rating").value(9.25))
                .andReturn().getResponse().getContentAsString();
        assertThat(listed).contains("\"rating\":9.25");
    }

    @Test
    void wholeAndHalfRatingsComeBackWithoutTrailingZeros() throws Exception {
        createExperienceVia(auth, gameId, run("Nine", "COMPLETADO", 9, 1.0, null, null, null, ""));
        createExperienceVia(auth, gameId, run("Ten", "COMPLETADO", new BigDecimal("10.00"), 1.0, null, null, null, ""));
        createExperienceVia(auth, gameId, run("Half", "COMPLETADO", new BigDecimal("8.5"), 1.0, null, null, null, ""));

        String listed = performAs(auth, get("/api/v1/games/{gameId}/experiences", gameId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(listed).contains("\"rating\":9,", "\"rating\":10,", "\"rating\":8.5,");
        assertThat(listed).doesNotContain("\"rating\":9.0", "\"rating\":10.0", "E+");
    }

    @ParameterizedTest
    @ValueSource(strings = {"9.255", "10.01", "-0.5", "11"})
    void invalidRatingsAreRejectedNotRounded(String rating) throws Exception {
        performAs(auth, post("/api/v1/games/{gameId}/experiences", gameId),
                "{\"runLabel\": \"Bad\", \"status\": \"COMPLETADO\", \"platform\": \"PC\", \"rating\": " + rating + "}")
                .andExpect(status().isBadRequest());

        performAs(auth, get("/api/v1/games/{gameId}/experiences", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void boundaryRatingsAreAccepted() throws Exception {
        createExperienceVia(auth, gameId, run("Zero", "COMPLETADO", 0, 1.0, null, null, null, ""));
        createExperienceVia(auth, gameId, run("Ten", "COMPLETADO", new BigDecimal("10.0"), 1.0, null, null, null, ""));
        createExperienceVia(auth, gameId, run("Two decimals", "COMPLETADO", new BigDecimal("0.01"), 1.0, null, null, null, ""));
    }

    @Test
    void decimalSentToAnIntegerFieldFailsInsteadOfTruncating() throws Exception {
        performAs(auth, post("/api/v1/games/{gameId}/experiences", gameId),
                "{\"runLabel\": \"Year\", \"status\": \"COMPLETADO\", \"platform\": \"PC\", \"year\": 2024.5}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void summaryAverageOfDecimalRatingsRoundsHalfUp() throws Exception {
        createExperienceVia(auth, gameId, run("A", "COMPLETADO", new BigDecimal("9.25"), 1.0, null, null, null, ""));
        createExperienceVia(auth, gameId, run("B", "COMPLETADO", new BigDecimal("8.5"), 1.0, null, null, null, ""));

        performAs(auth, get("/api/v1/stats/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(8.88));

        performAs(auth, get("/api/v1/games/{id}", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bestRating").value(9.25));
    }

    @Test
    void onlinePlaytimeGeneralRatingAcceptsTwoDecimals() throws Exception {
        performAs(auth, put("/api/v1/games/{gameId}/online-playtime", gameId),
                "{\"totalHours\": 10.0, \"generalRating\": 7.75}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generalRating").value(7.75));

        performAs(auth, get("/api/v1/games/{gameId}/online-playtime", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generalRating").value(7.75));

        performAs(auth, put("/api/v1/games/{gameId}/online-playtime", gameId),
                "{\"totalHours\": 10.0, \"generalRating\": 7.755}")
                .andExpect(status().isBadRequest());
        performAs(auth, put("/api/v1/games/{gameId}/online-playtime", gameId),
                "{\"totalHours\": 10.0, \"generalRating\": 10.5}")
                .andExpect(status().isBadRequest());
    }
}
