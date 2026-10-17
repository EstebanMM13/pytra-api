package com.estebanmm13.pytra_api.psnsync.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PsnOnlineIdsTest {

    @ParameterizedTest
    @ValueSource(strings = {"EstebanK13", "abc", "Sixteen_chars-16", "a-b_c", "123"})
    void acceptsValidOnlineIds(String onlineId) {
        assertThat(PsnOnlineIds.normalize(onlineId)).isEqualTo(onlineId);
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(PsnOnlineIds.normalize("  EstebanK13 ")).isEqualTo("EstebanK13");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ab", "seventeen_chars17", "with space", "dot.name", "ñandú", "../etc", "a/b", "id?x=1"})
    void rejectsInvalidOnlineIds(String onlineId) {
        assertThat(PsnOnlineIds.normalize(onlineId)).isNull();
    }
}
