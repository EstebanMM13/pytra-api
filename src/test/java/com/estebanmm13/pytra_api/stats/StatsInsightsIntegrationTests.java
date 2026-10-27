package com.estebanmm13.pytra_api.stats;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.platforms.repository.PlatformRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Activity, platform, developer, release era, completion and rating breakdowns. */
class StatsInsightsIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private PlatformRepository platformRepository;

    private String auth;
    private Long pcId;
    private Long ps5Id;

    @BeforeEach
    void createUser() {
        User user = createVerifiedUser("insights");
        auth = bearer(user);
        pcId = platformRepository.findByLegacyCodeIgnoreCase("PC").orElseThrow().getId();
        ps5Id = platformRepository.findByLegacyCodeIgnoreCase("PS5").orElseThrow().getId();
    }

    private Long createOnlineGame(String name) throws Exception {
        return readId(performAs(auth, post("/api/v1/games"), "{\"name\": \"" + name + "\", \"category\": \"ONLINE\"}")
                .andExpect(status().isCreated()).andReturn());
    }

    private void logDay(String date, String gamesJson) throws Exception {
        performAs(auth, put("/api/v1/diary/{date}", date), "{\"games\": [" + gamesJson + "]}")
                .andExpect(status().isOk());
    }

    /** A run on a legacy-coded platform (PC, PS5, ...); null rating is omitted. */
    private static String runOn(String platform, String status, Number rating, double hours, int year) {
        return "{\"runLabel\": \"" + platform + " " + status + " " + year + "\", \"status\": \"" + status
                + "\", \"platform\": \"" + platform + "\", \"hours\": " + hours + ", \"year\": " + year
                + (rating != null ? ", \"rating\": " + rating : "") + "}";
    }

    private void putOnlinePlaytime(Long gameId, String totalHours, String platformIdsJson) throws Exception {
        performAs(auth, put("/api/v1/games/{gameId}/online-playtime", gameId),
                "{\"totalHours\": " + totalHours + ", \"platformIds\": [" + platformIdsJson + "]}")
                .andExpect(status().isOk());
    }

    private static String line(Long gameId, String hours) {
        return "{\"gameId\": " + gameId + ", \"hours\": " + hours + "}";
    }

    // ---------------------------------------------------------------- activity

    @Test
    void activityListsPlayDaysWeekdaysAndStreaks() throws Exception {
        Long arena = createOnlineGame("Activity Arena");
        Long league = createOnlineGame("Activity League");
        // 2024-01-01 is a Monday. Three consecutive days, a gap, then two more.
        logDay("2024-01-01", line(arena, "2") + ", " + line(league, "1.5"));
        logDay("2024-01-02", line(arena, "1"));
        logDay("2024-01-03", line(arena, "0.5"));
        logDay("2024-01-08", line(league, "4"));
        logDay("2024-01-09", line(league, "1"));
        // Another year: not in the 2024 days, but part of the all-time streaks.
        logDay("2023-12-31", line(arena, "1"));
        // A note-only day is not a play day.
        performAs(auth, put("/api/v1/diary/{date}", "2024-01-04"), "{\"note\": \"Rest day\", \"games\": []}")
                .andExpect(status().isOk());

        performAs(auth, get("/api/v1/stats/activity").param("year", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2024))
                .andExpect(jsonPath("$.activeDays").value(5))
                .andExpect(jsonPath("$.totalHours").value(10.0))
                .andExpect(jsonPath("$.days.length()").value(5))
                .andExpect(jsonPath("$.days[0].date").value("2024-01-01"))
                .andExpect(jsonPath("$.days[0].hours").value(3.5))
                .andExpect(jsonPath("$.days[0].gameCount").value(2))
                .andExpect(jsonPath("$.days[4].date").value("2024-01-09"))
                // Longest: 2023-12-31 .. 2024-01-03 (crosses the year).
                .andExpect(jsonPath("$.longestStreak.length").value(4))
                .andExpect(jsonPath("$.longestStreak.startDate").value("2023-12-31"))
                .andExpect(jsonPath("$.longestStreak.endDate").value("2024-01-03"))
                // Nothing logged around today.
                .andExpect(jsonPath("$.currentStreak.length").value(0))
                .andExpect(jsonPath("$.currentStreak.startDate").value(nullValue()))
                .andExpect(jsonPath("$.weekdays.length()").value(7))
                .andExpect(jsonPath("$.weekdays[0].name").value("MONDAY"))
                .andExpect(jsonPath("$.weekdays[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$.weekdays[0].daysPlayed").value(2))
                .andExpect(jsonPath("$.weekdays[0].hours").value(7.5))
                .andExpect(jsonPath("$.weekdays[1].daysPlayed").value(2))
                .andExpect(jsonPath("$.weekdays[1].hours").value(2.0))
                .andExpect(jsonPath("$.weekdays[6].name").value("SUNDAY"))
                .andExpect(jsonPath("$.weekdays[6].daysPlayed").value(0));
    }

    @Test
    void activityCurrentStreakEndsTodayOrYesterday() throws Exception {
        Long arena = createOnlineGame("Streak Arena");
        LocalDate today = LocalDate.now();
        logDay(today.minusDays(2).toString(), line(arena, "1"));
        logDay(today.minusDays(1).toString(), line(arena, "1"));

        performAs(auth, get("/api/v1/stats/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(today.getYear()))
                .andExpect(jsonPath("$.currentStreak.length").value(2))
                .andExpect(jsonPath("$.currentStreak.startDate").value(today.minusDays(2).toString()))
                .andExpect(jsonPath("$.currentStreak.endDate").value(today.minusDays(1).toString()));
    }

    @Test
    void activityOfAnEmptyYearHasNoDaysAndRejectsInvalidYears() throws Exception {
        performAs(auth, get("/api/v1/stats/activity").param("year", "2020"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(0))
                .andExpect(jsonPath("$.totalHours").value(0.0))
                .andExpect(jsonPath("$.longestStreak.length").value(0))
                .andExpect(jsonPath("$.weekdays.length()").value(7));

        performAs(auth, get("/api/v1/stats/activity").param("year", "1900"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- platforms

    @Test
    void byPlatformSplitsOnlineHoursAcrossItsPlatforms() throws Exception {
        Long quest = createGameVia(auth, "Platform Quest", "");
        createExperienceVia(auth, quest, runOn("PC", "COMPLETADO", 8, 10.0, 2023));
        createExperienceVia(auth, quest, runOn("PS5", "ABANDONADO", 6, 4.0, 2024));
        Long arena = createOnlineGame("Platform Arena");
        putOnlinePlaytime(arena, "30", pcId + ", " + ps5Id);
        // +2 online hours dated 2024 (the playtime ends at 32h, 16h per platform).
        logDay("2024-02-01", line(arena, "2"));
        Long lobby = createOnlineGame("Platform Lobby");
        putOnlinePlaytime(lobby, "5", "");

        performAs(auth, get("/api/v1/stats/by-platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(nullValue()))
                .andExpect(jsonPath("$.unassignedOnlineHours").value(5.0))
                .andExpect(jsonPath("$.platforms.length()").value(2))
                .andExpect(jsonPath("$.platforms[0].platformId").value(pcId))
                .andExpect(jsonPath("$.platforms[0].name").value("PC"))
                .andExpect(jsonPath("$.platforms[0].family").value("PC"))
                .andExpect(jsonPath("$.platforms[0].hours").value(26.0))
                .andExpect(jsonPath("$.platforms[0].runHours").value(10.0))
                .andExpect(jsonPath("$.platforms[0].onlineHours").value(16.0))
                .andExpect(jsonPath("$.platforms[0].gameCount").value(2))
                .andExpect(jsonPath("$.platforms[0].runCount").value(1))
                .andExpect(jsonPath("$.platforms[0].completedRunCount").value(1))
                .andExpect(jsonPath("$.platforms[0].ratedRunCount").value(1))
                .andExpect(jsonPath("$.platforms[0].averageRating").value(8))
                .andExpect(jsonPath("$.platforms[1].platformId").value(ps5Id))
                .andExpect(jsonPath("$.platforms[1].hours").value(20.0))
                .andExpect(jsonPath("$.platforms[1].completedRunCount").value(0))
                .andExpect(jsonPath("$.platforms[1].averageRating").value(6))
                // byYear: oldest first; 2024 has the PS5 run plus the dated online hours split in two.
                .andExpect(jsonPath("$.byYear.length()").value(2))
                .andExpect(jsonPath("$.byYear[0].year").value(2023))
                .andExpect(jsonPath("$.byYear[0].totalHours").value(10.0))
                .andExpect(jsonPath("$.byYear[0].platforms[0].platformId").value(pcId))
                .andExpect(jsonPath("$.byYear[1].year").value(2024))
                .andExpect(jsonPath("$.byYear[1].totalHours").value(6.0))
                .andExpect(jsonPath("$.byYear[1].unassignedOnlineHours").value(0.0))
                .andExpect(jsonPath("$.byYear[1].platforms[0].platformId").value(ps5Id))
                .andExpect(jsonPath("$.byYear[1].platforms[0].hours").value(5.0))
                .andExpect(jsonPath("$.byYear[1].platforms[1].platformId").value(pcId))
                .andExpect(jsonPath("$.byYear[1].platforms[1].hours").value(1.0));

        performAs(auth, get("/api/v1/stats/by-platform").param("year", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2024))
                .andExpect(jsonPath("$.unassignedOnlineHours").value(0.0))
                .andExpect(jsonPath("$.platforms.length()").value(2))
                .andExpect(jsonPath("$.platforms[0].platformId").value(ps5Id))
                .andExpect(jsonPath("$.platforms[0].hours").value(5.0))
                .andExpect(jsonPath("$.platforms[0].runHours").value(4.0))
                .andExpect(jsonPath("$.platforms[0].onlineHours").value(1.0))
                .andExpect(jsonPath("$.platforms[1].platformId").value(pcId))
                .andExpect(jsonPath("$.platforms[1].runCount").value(0))
                .andExpect(jsonPath("$.platforms[1].averageRating").value(nullValue()))
                // byYear always covers every year.
                .andExpect(jsonPath("$.byYear.length()").value(2));
    }

    // ---------------------------------------------------------------- developers & release eras

    @Test
    void byDeveloperGroupsTrimmedNamesCaseInsensitivelyAndHonorsTheThreshold() throws Exception {
        Long souls = createGameVia(auth, "Dev Souls", ", \"developer\": \"FromSoftware\", \"releaseDate\": \"2011-10-04\"");
        createExperienceVia(auth, souls, run("A", "COMPLETADO", 9, 50.0, 2020, null, null, ""));
        Long ring = createGameVia(auth, "Dev Ring", ", \"developer\": \"  fromsoftware \", \"releaseDate\": \"2022-02-25\"");
        createExperienceVia(auth, ring, run("A", "COMPLETADO", 8, 100.0, 2022, null, null, ""));
        Long zelda = createGameVia(auth, "Dev Zelda", ", \"developer\": \"Nintendo\", \"releaseDate\": \"1998-11-21\"");
        createExperienceVia(auth, zelda, run("A", "COMPLETADO", null, 20.0, 2021, null, null, ""));
        performAs(auth, put("/api/v1/games/{gameId}/online-playtime", zelda), "{\"totalHours\": 5, \"generalRating\": 7}")
                .andExpect(status().isOk());
        createGameVia(auth, "Dev Undated", "");

        performAs(auth, get("/api/v1/stats/by-developer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minRatedGames").value(2))
                .andExpect(jsonPath("$.topByHours.length()").value(2))
                .andExpect(jsonPath("$.topByHours[0].developer").value("FromSoftware"))
                .andExpect(jsonPath("$.topByHours[0].gameCount").value(2))
                .andExpect(jsonPath("$.topByHours[0].hours").value(150.0))
                .andExpect(jsonPath("$.topByHours[0].ratedGameCount").value(2))
                .andExpect(jsonPath("$.topByHours[0].averageRating").value(8.5))
                .andExpect(jsonPath("$.topByHours[1].developer").value("Nintendo"))
                .andExpect(jsonPath("$.topByHours[1].hours").value(25.0))
                .andExpect(jsonPath("$.topByHours[1].averageRating").value(7))
                .andExpect(jsonPath("$.topByRating.length()").value(1))
                .andExpect(jsonPath("$.topByRating[0].developer").value("FromSoftware"));

        performAs(auth, get("/api/v1/stats/by-developer").param("minRatedGames", "1").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minRatedGames").value(1))
                .andExpect(jsonPath("$.topByHours.length()").value(1))
                .andExpect(jsonPath("$.topByRating.length()").value(1))
                .andExpect(jsonPath("$.topByRating[0].developer").value("FromSoftware"));
    }

    @Test
    void byReleaseEraGroupsConfirmedGamesByDecadeAndYear() throws Exception {
        Long a = createGameVia(auth, "Era A", ", \"releaseDate\": \"1998-01-10\"");
        createExperienceVia(auth, a, run("A", "COMPLETADO", 6, 10.0, 2020, null, null, ""));
        Long b = createGameVia(auth, "Era B", ", \"releaseDate\": \"1991-05-01\"");
        createExperienceVia(auth, b, run("A", "COMPLETADO", 9, 5.0, 2020, null, null, ""));
        Long c = createGameVia(auth, "Era C", ", \"releaseDate\": \"2004-03-01\"");
        createExperienceVia(auth, c, run("A", "ABANDONADO", null, 2.0, 2020, null, null, ""));
        createGameVia(auth, "Era Undated", "");

        performAs(auth, get("/api/v1/stats/by-release-era"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.undatedGameCount").value(1))
                .andExpect(jsonPath("$.byDecade.length()").value(2))
                .andExpect(jsonPath("$.byDecade[0].decade").value(1990))
                .andExpect(jsonPath("$.byDecade[0].gameCount").value(2))
                .andExpect(jsonPath("$.byDecade[0].hours").value(15.0))
                .andExpect(jsonPath("$.byDecade[0].ratedGameCount").value(2))
                .andExpect(jsonPath("$.byDecade[0].averageRating").value(7.5))
                .andExpect(jsonPath("$.byDecade[1].decade").value(2000))
                .andExpect(jsonPath("$.byDecade[1].ratedGameCount").value(0))
                .andExpect(jsonPath("$.byDecade[1].averageRating").value(nullValue()))
                .andExpect(jsonPath("$.byYear.length()").value(3))
                .andExpect(jsonPath("$.byYear[0].year").value(1991))
                .andExpect(jsonPath("$.byYear[2].year").value(2004));
    }
}
