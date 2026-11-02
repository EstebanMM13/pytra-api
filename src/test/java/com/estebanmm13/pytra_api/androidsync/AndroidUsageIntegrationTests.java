package com.estebanmm13.pytra_api.androidsync;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.config.AppTimeZone;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Android usage sync end to end against a real Postgres: links, baselines (V43/V44), runs and diary. */
class AndroidUsageIntegrationTests extends AbstractIntegrationTest {

    private static final String PACKAGE = "com.example.androidgame";

    @Autowired
    private ExperienceRepository experienceRepository;
    @Autowired
    private AppTimeZone appTimeZone;

    private User alice;
    private String aliceAuth;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        alice = createVerifiedUser("androidsync");
        aliceAuth = bearer(alice);
        today = LocalDate.now(appTimeZone.zone());
    }

    @Test
    void syncedMinutesGoToTheMobileRunOnceAndShowAsARecordOnlyDiaryLine() throws Exception {
        Long gameId = createGameVia(aliceAuth, "Android Game", "");
        performAs(aliceAuth, post("/api/v1/android-links"),
                "{\"packageName\": \"" + PACKAGE + "\", \"gameId\": " + gameId + "}")
                .andExpect(status().isCreated());

        sync(90).andExpect(jsonPath("$.recorded").value(1)).andExpect(jsonPath("$.ignored").value(1));
        // Same total again: nothing new.
        sync(90).andExpect(jsonPath("$.recorded").value(0)).andExpect(jsonPath("$.ignored").value(2));
        sync(120).andExpect(jsonPath("$.recorded").value(1));

        List<Experience> runs = experienceRepository.findAllByGameIdAndUserId(gameId, alice.getId());
        assertThat(runs).hasSize(1);
        assertThat(runs.getFirst().getRunLabel()).isEqualTo("Importado de Android");
        assertThat(runs.getFirst().getPlatform().getLegacyCode()).isEqualTo("MOBILE");
        assertThat(runs.getFirst().getHours()).isEqualTo(2.0);

        performAs(aliceAuth, get("/api/v1/diary/{date}", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games.length()").value(1))
                .andExpect(jsonPath("$.games[0].gameId").value(gameId))
                .andExpect(jsonPath("$.games[0].hours").value(2.0))
                .andExpect(jsonPath("$.games[0].countsHours").value(false))
                .andExpect(jsonPath("$.games[0].autoSynced").value(true));

        performAs(aliceAuth, get("/api/v1/android-links"))
                .andExpect(jsonPath("$[0].packageName").value(PACKAGE))
                .andExpect(jsonPath("$[0].gameId").value(gameId));
        performAs(aliceAuth, delete("/api/v1/android-links/{packageName}", PACKAGE)).andExpect(status().isNoContent());
        performAs(aliceAuth, get("/api/v1/android-links")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anotherUsersGameCannotBeLinkedAndAPackageLinksOnce() throws Exception {
        User bob = createVerifiedUser("androidsyncbob");
        Long bobGame = createGameVia(bearer(bob), "Bob Game", "");
        Long aliceGame = createGameVia(aliceAuth, "Alice Game", "");
        Long aliceOther = createGameVia(aliceAuth, "Alice Other", "");

        performAs(aliceAuth, post("/api/v1/android-links"),
                "{\"packageName\": \"" + PACKAGE + "\", \"gameId\": " + bobGame + "}")
                .andExpect(status().isNotFound());
        performAs(aliceAuth, post("/api/v1/android-links"),
                "{\"packageName\": \"" + PACKAGE + "\", \"gameId\": " + aliceGame + "}")
                .andExpect(status().isCreated());
        performAs(aliceAuth, post("/api/v1/android-links"),
                "{\"packageName\": \"" + PACKAGE + "\", \"gameId\": " + aliceOther + "}")
                .andExpect(status().isConflict());
    }

    @Test
    void syncRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/android-usage/sync").contentType("application/json").content("[]"))
                .andExpect(status().isUnauthorized());
    }

    /** Today's total for the linked package plus an unlinked one. */
    private org.springframework.test.web.servlet.ResultActions sync(int minutes) throws Exception {
        return performAs(aliceAuth, post("/api/v1/android-usage/sync"), """
                [{"date": "%s", "packageName": "%s", "minutes": %d},
                 {"date": "%s", "packageName": "com.whatsapp", "minutes": 300}]
                """.formatted(today, PACKAGE, minutes, today))
                .andExpect(status().isOk());
    }
}
