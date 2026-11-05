package com.estebanmm13.pytra_api.config;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Server timestamps are UTC: they go out with a {@code Z}, and come back in with or without it. */
class UtcDateTimeModuleTest {

    private final JsonMapper json = JsonMapper.builder().addModule(new UtcDateTimeModule()).build();

    private record Holder(LocalDateTime at) {
    }

    @Test
    void localDateTimeIsWrittenAsAUtcInstant() {
        assertThat(json.writeValueAsString(new Holder(LocalDateTime.of(2026, 11, 5, 21, 0, 0))))
                .isEqualTo("{\"at\":\"2026-11-05T21:00:00Z\"}");
        assertThat(json.writeValueAsString(new Holder(LocalDateTime.of(2026, 11, 5, 21, 0, 0, 123_000_000))))
                .isEqualTo("{\"at\":\"2026-11-05T21:00:00.123Z\"}");
        assertThat(json.writeValueAsString(new Holder(null))).isEqualTo("{\"at\":null}");
    }

    @Test
    void localDateTimeIsReadWithOrWithoutTheZ() {
        LocalDateTime expected = LocalDateTime.of(2026, 11, 5, 21, 0, 0);

        assertThat(json.readValue("{\"at\":\"2026-11-05T21:00:00Z\"}", Holder.class).at()).isEqualTo(expected);
        assertThat(json.readValue("{\"at\":\"2026-11-05T21:00:00\"}", Holder.class).at()).isEqualTo(expected);
    }

    @Test
    void anExplicitOffsetIsConvertedToUtc() {
        assertThat(json.readValue("{\"at\":\"2026-11-05T22:00:00+01:00\"}", Holder.class).at())
                .isEqualTo(LocalDateTime.of(2026, 11, 5, 21, 0, 0));
    }
}
