package com.estebanmm13.pytra_api.games;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An update response carries the refreshed {@code updatedAt}, the same value a later read returns
 * (open-in-view is off, so this relies on the service flushing inside its own transaction).
 */
class UpdateTimestampIntegrationTests extends AbstractIntegrationTest {

    private String auth;

    @BeforeEach
    void setUp() {
        auth = bearer(createVerifiedUser("stamp"));
    }

    private LocalDateTime updatedAt(String json) {
        // Millisecond precision: the stored column may hold fewer fractional digits than the JVM clock.
        return LocalDateTime.parse(JsonPath.<String>read(json, "$.updatedAt")).truncatedTo(ChronoUnit.MILLIS);
    }

    @Test
    void gameUpdateReturnsTheNewUpdatedAt() throws Exception {
        Long gameId = createGameVia(auth, "Hollow Knight", "");
        LocalDateTime before = updatedAt(performAs(auth, get("/api/v1/games/{id}", gameId))
                .andReturn().getResponse().getContentAsString());
        Thread.sleep(20);

        String updated = performAs(auth, put("/api/v1/games/{id}", gameId),
                        "{\"name\": \"Hollow Knight Silksong\", \"category\": \"SINGLEPLAYER\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String reread = performAs(auth, get("/api/v1/games/{id}", gameId))
                .andReturn().getResponse().getContentAsString();

        assertThat(updatedAt(updated)).isAfter(before).isEqualTo(updatedAt(reread));
    }

    @Test
    void sagaUpdateReturnsTheNewUpdatedAt() throws Exception {
        String created = performAs(auth, post("/api/v1/sagas"), "{\"name\": \"Souls\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long sagaId = ((Number) JsonPath.read(created, "$.id")).longValue();
        LocalDateTime before = updatedAt(created);
        Thread.sleep(20);

        String updated = performAs(auth, put("/api/v1/sagas/{id}", sagaId), "{\"name\": \"Soulsborne\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String reread = performAs(auth, get("/api/v1/sagas/{id}", sagaId))
                .andReturn().getResponse().getContentAsString();

        assertThat(updatedAt(updated)).isAfter(before).isEqualTo(updatedAt(reread));
    }
}
