package com.estebanmm13.pytra_api.achievements;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import com.estebanmm13.pytra_api.auth.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Unlocked achievements stay stored with their first unlock date (real database). */
class AchievementIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;
    private String auth;

    @BeforeEach
    void createUser() {
        user = createVerifiedUser("achiever");
        auth = bearer(user);
    }

    @Test
    void aNewUserHasNoUnlockedAchievements() throws Exception {
        performAs(auth, get("/api/v1/achievements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void recordingAgainKeepsTheFirstDate() throws Exception {
        performAs(auth, post("/api/v1/achievements"), "{\"ids\": [\"multitasker\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("multitasker"));
        LocalDateTime first = LocalDateTime.of(2026, 3, 2, 9, 0, 30);
        jdbcTemplate.update("UPDATE unlocked_achievements SET unlocked_at = ? WHERE user_id = ?", first, user.getId());

        performAs(auth, post("/api/v1/achievements"), "{\"ids\": [\"multitasker\", \"years10\", \"years10\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("multitasker"))
                .andExpect(jsonPath("$[0].unlockedAt").value("2026-03-02T09:00:30Z"))
                .andExpect(jsonPath("$[1].id").value("years10"));

        MvcResult list = performAs(auth, get("/api/v1/achievements")).andExpect(status().isOk()).andReturn();
        assertThat(list.getResponse().getContentAsString()).contains("\"2026-03-02T09:00:30\"");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM unlocked_achievements WHERE user_id = ?", Long.class, user.getId())).isEqualTo(2);
    }

    @Test
    void anUnsafeIdIsRejectedAndNothingIsStored() throws Exception {
        performAs(auth, post("/api/v1/achievements"), "{\"ids\": [\"ok\", \"no spaces\"]}")
                .andExpect(status().isBadRequest());

        performAs(auth, get("/api/v1/achievements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
