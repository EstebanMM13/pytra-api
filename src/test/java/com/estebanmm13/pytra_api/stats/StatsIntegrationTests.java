package com.estebanmm13.pytra_api.stats;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import com.estebanmm13.pytra_api.auth.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Year;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Stats added for the web redesign: averages, status counts, year summary, highlights, notes, in-progress. */
class StatsIntegrationTests extends AbstractIntegrationTest {

    private String auth;

    @BeforeEach
    void createUser() {
        User user = createVerifiedUser("stats");
        auth = bearer(user);
    }

    @Test
    void summaryIncludesAverageRatingAndStatusCounts() throws Exception {
        Long gameId = createGameVia(auth, "Summary Game", "");
        createExperienceVia(auth, gameId, run("A", "COMPLETADO", 9, 10.0, null, null, null, ", \"replay\": true, \"platinum\": true"));
        createExperienceVia(auth, gameId, run("B", "ABANDONADO", 8, 1.0, null, null, null, ""));
        createExperienceVia(auth, gameId, run("C", "EN_CURSO", 6, 1.0, null, null, null, ""));
        createExperienceVia(auth, gameId, run("D", "PENDIENTE", null, 0.0, null, null, null, ""));

        performAs(auth, get("/api/v1/stats/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExperiences").value(4))
                .andExpect(jsonPath("$.averageRating").value(7.67))
                .andExpect(jsonPath("$.replayCount").value(1))
                .andExpect(jsonPath("$.totalPlatinums").value(1))
                .andExpect(jsonPath("$.completedCount").value(1))
                .andExpect(jsonPath("$.abandonedCount").value(1))
                .andExpect(jsonPath("$.inProgressCount").value(1));
    }

    @Test
    void averageRatingIsNullWhenNothingIsRated() throws Exception {
        performAs(auth, get("/api/v1/stats/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(nullValue()));
    }

    @Test
    void byYearAndYearsUseYearFieldThenEndDateThenStartDate() throws Exception {
        Long gameId = createGameVia(auth, "Year Game", "");
        createExperienceVia(auth, gameId, run("Explicit", "COMPLETADO", 9, 10.0, 2024, null, "2023-02-01", ""));
        createExperienceVia(auth, gameId, run("Explicit 2", "COMPLETADO", 6, 2.0, 2024, null, null, ""));
        createExperienceVia(auth, gameId, run("From end", "COMPLETADO", 7, 3.0, null, "2022-01-01", "2023-05-01", ""));
        createExperienceVia(auth, gameId, run("From start", "EN_CURSO", null, 1.0, null, "2021-03-01", null, ""));
        createExperienceVia(auth, gameId, run("Undated", "PENDIENTE", 10, 50.0, null, null, null, ""));

        performAs(auth, get("/api/v1/stats/years"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", contains(2024, 2023, 2021)));

        performAs(auth, get("/api/v1/stats/by-year"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].year").value(2024))
                .andExpect(jsonPath("$[0].experienceCount").value(2))
                .andExpect(jsonPath("$[0].totalHours").value(12.0))
                .andExpect(jsonPath("$[0].averageRating").value(7.5))
                .andExpect(jsonPath("$[1].year").value(2023))
                .andExpect(jsonPath("$[1].averageRating").value(7.0))
                .andExpect(jsonPath("$[2].year").value(2021))
                .andExpect(jsonPath("$[2].averageRating").value(nullValue()));
    }

    @Test
    void yearSummaryBucketsHoursByMonthAndOrdersRunsByRating() throws Exception {
        Long gameId = createGameVia(auth, "Month Game", "");
        createExperienceVia(auth, gameId, run("March", "COMPLETADO", 7, 10.0, null, null, "2024-03-15", ", \"platinum\": true"));
        createExperienceVia(auth, gameId, run("July", "EN_CURSO", 9, 5.0, 2024, "2024-07-01", null, ""));
        createExperienceVia(auth, gameId, run("Also March", "ABANDONADO", null, 1.5, 2024, null, "2024-03-02", ""));
        createExperienceVia(auth, gameId, run("No month", "COMPLETADO", 8, 2.5, 2024, "2023-12-01", "2025-01-10", ""));
        createExperienceVia(auth, gameId, run("Other year", "COMPLETADO", 10, 99.0, 2023, null, null, ""));

        performAs(auth, get("/api/v1/stats/years/2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2024))
                .andExpect(jsonPath("$.totalHours").value(19.0))
                .andExpect(jsonPath("$.experienceCount").value(4))
                .andExpect(jsonPath("$.completedCount").value(2))
                .andExpect(jsonPath("$.abandonedCount").value(1))
                .andExpect(jsonPath("$.platinumCount").value(1))
                .andExpect(jsonPath("$.averageRating").value(8.0))
                .andExpect(jsonPath("$.hoursWithoutMonth").value(2.5))
                .andExpect(jsonPath("$.months", hasSize(12)))
                .andExpect(jsonPath("$.months[0].month").value(1))
                .andExpect(jsonPath("$.months[0].hours").value(0.0))
                .andExpect(jsonPath("$.months[2].month").value(3))
                .andExpect(jsonPath("$.months[2].hours").value(11.5))
                .andExpect(jsonPath("$.months[6].hours").value(5.0))
                .andExpect(jsonPath("$.months[11].month").value(12))
                .andExpect(jsonPath("$.experiences[*].runLabel", contains("July", "No month", "March", "Also March")))
                .andExpect(jsonPath("$.experiences[0].gameName").value("Month Game"))
                .andExpect(jsonPath("$.experiences[0].gameId").value(gameId))
                .andExpect(jsonPath("$.experiences[0].month").value(7))
                .andExpect(jsonPath("$.experiences[0].platform").value("PC"));
    }

    @Test
    void yearWithoutDataAnswersZeros() throws Exception {
        performAs(auth, get("/api/v1/stats/years/2001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHours").value(0.0))
                .andExpect(jsonPath("$.experienceCount").value(0))
                .andExpect(jsonPath("$.averageRating").value(nullValue()))
                .andExpect(jsonPath("$.months", hasSize(12)))
                .andExpect(jsonPath("$.experiences", hasSize(0)))
                .andExpect(jsonPath("$.goty").value(nullValue()))
                .andExpect(jsonPath("$.topRated", hasSize(0)))
                .andExpect(jsonPath("$.note.summary").value(nullValue()))
                .andExpect(jsonPath("$.note.highlights").value(nullValue()));
    }

    @Test
    void highlightsUseCompletedInYearAndMostPlayedUsesTheYearField() throws Exception {
        Long sagaId = readId(performAs(auth, post("/api/v1/sagas"), "{\"name\": \"Highlight Saga\"}")
                .andExpect(status().isCreated()).andReturn());
        Long finished = createGameVia(auth, "Finished", ", \"sagaId\": " + sagaId);
        Long grinding = createGameVia(auth, "Grinding", "");
        Long meh = createGameVia(auth, "Meh", "");

        // Completed in 2024 (endDate), no year field: rated highlights only.
        createExperienceVia(auth, finished, run("Run", "COMPLETADO", 9, 30.0, null, null, "2024-04-01", ""));
        // Year field 2024 but still in progress: most played only.
        createExperienceVia(auth, grinding, run("Run", "EN_CURSO", 10, 80.0, 2024, null, null, ""));
        createExperienceVia(auth, meh, run("Run", "COMPLETADO", 6, 4.0, 2024, null, "2024-09-01", ""));

        performAs(auth, get("/api/v1/stats/years/2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goty.gameId").value(finished))
                .andExpect(jsonPath("$.goty.avgRating").value(9.0))
                .andExpect(jsonPath("$.goty.experienceCount").value(1))
                .andExpect(jsonPath("$.goty.totalHours").value(30.0))
                .andExpect(jsonPath("$.topRated[*].gameName", contains("Finished", "Meh")))
                .andExpect(jsonPath("$.mostPlayed[*].gameName", contains("Grinding", "Meh")))
                .andExpect(jsonPath("$.mostPlayed[0].totalHours").value(80.0))
                .andExpect(jsonPath("$.topSagas", hasSize(1)))
                .andExpect(jsonPath("$.topSagas[0].sagaId").value(sagaId))
                .andExpect(jsonPath("$.topSagas[0].experienceCount").value(1))
                .andExpect(jsonPath("$.surprises", hasSize(0)))
                .andExpect(jsonPath("$.disappointments[*].gameName", contains("Meh")))
                .andExpect(jsonPath("$.disappointments[0].avgRating").value(6.0));
    }

    @Test
    void yearNoteIsUpsertedAndReturnedInTheSummary() throws Exception {
        performAs(auth, put("/api/v1/stats/years/2024/note"),
                "{\"summary\": \"First draft\", \"highlights\": \"Elden Ring\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("First draft"))
                .andExpect(jsonPath("$.highlights").value("Elden Ring"));

        performAs(auth, put("/api/v1/stats/years/2024/note"), "{\"summary\": \"Final\", \"highlights\": \"  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Final"))
                .andExpect(jsonPath("$.highlights").value(nullValue()));

        performAs(auth, get("/api/v1/stats/years/2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note.summary").value("Final"))
                .andExpect(jsonPath("$.note.highlights").value(nullValue()))
                .andExpect(jsonPath("$.note.updatedAt").exists());

        performAs(auth, get("/api/v1/stats/years/2023"))
                .andExpect(jsonPath("$.note.summary").value(nullValue()));
    }

    @Test
    void yearOutsideTheAllowedRangeIs400() throws Exception {
        int tooLate = Year.now().getValue() + 2;
        performAs(auth, get("/api/v1/stats/years/1969")).andExpect(status().isBadRequest());
        performAs(auth, get("/api/v1/stats/years/{year}", tooLate)).andExpect(status().isBadRequest());
        performAs(auth, put("/api/v1/stats/years/1969/note"), "{\"summary\": \"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_YEAR"));
        performAs(auth, put("/api/v1/stats/years/{year}/note", Year.now().getValue() + 1), "{\"summary\": \"x\"}")
                .andExpect(status().isOk());
    }

    @Test
    void yearNoteTextIsCapped() throws Exception {
        String tooLong = "a".repeat(10_001);
        performAs(auth, put("/api/v1/stats/years/2024/note"), "{\"summary\": \"" + tooLong + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void inProgressListsOnlyRunningExperiencesAcrossGames() throws Exception {
        Long first = createGameVia(auth, "First", ", \"coverImageUrl\": \"https://img.test/first.png\"");
        Long second = createGameVia(auth, "Second", "");
        Long older = createExperienceVia(auth, first, run("Older", "EN_CURSO", null, 3.0, null, "2024-01-01", null, ""));
        Long newer = createExperienceVia(auth, second, run("Newer", "EN_CURSO", null, 1.5, null, "2025-02-01", null, ""));
        createExperienceVia(auth, first, run("Done", "COMPLETADO", 8, 10.0, 2024, null, null, ""));

        performAs(auth, get("/api/v1/stats/in-progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].experienceId").value(newer))
                .andExpect(jsonPath("$[0].gameName").value("Second"))
                .andExpect(jsonPath("$[0].runLabel").value("Newer"))
                .andExpect(jsonPath("$[0].platform").value("PC"))
                .andExpect(jsonPath("$[0].startDate").value("2025-02-01"))
                .andExpect(jsonPath("$[0].hours").value(1.5))
                .andExpect(jsonPath("$[1].experienceId").value(older))
                .andExpect(jsonPath("$[1].coverImageUrl").value("https://img.test/first.png"));
    }
}
