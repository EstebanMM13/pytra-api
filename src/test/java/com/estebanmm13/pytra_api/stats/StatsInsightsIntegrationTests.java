package com.estebanmm13.pytra_api.stats;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import com.estebanmm13.pytra_api.auth.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Activity, platform, developer, release era, completion and rating breakdowns. */
class StatsInsightsIntegrationTests extends AbstractIntegrationTest {

    private String auth;

    @BeforeEach
    void createUser() {
        User user = createVerifiedUser("insights");
        auth = bearer(user);
    }

    private Long createOnlineGame(String name) throws Exception {
        return readId(performAs(auth, post("/api/v1/games"), "{\"name\": \"" + name + "\", \"category\": \"ONLINE\"}")
                .andExpect(status().isCreated()).andReturn());
    }

    private void logDay(String date, String gamesJson) throws Exception {
        performAs(auth, put("/api/v1/diary/{date}", date), "{\"games\": [" + gamesJson + "]}")
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
}
