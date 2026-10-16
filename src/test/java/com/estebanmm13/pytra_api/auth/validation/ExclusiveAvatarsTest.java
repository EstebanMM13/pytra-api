package com.estebanmm13.pytra_api.auth.validation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExclusiveAvatarsTest {

    private static final String KEY = "estebanexplorador";
    private static final Long OWNER_ID = 7L;

    private final ExclusiveAvatars avatars = new ExclusiveAvatars(KEY + ":" + OWNER_ID);

    @Test
    void ownerMayPickTheExclusiveKey() {
        assertTrue(avatars.isAllowed(KEY, OWNER_ID));
    }

    @Test
    void otherUsersCannotPickTheExclusiveKey() {
        assertFalse(avatars.isAllowed(KEY, 8L));
        assertFalse(avatars.isAllowed(KEY, null));
        assertFalse(avatars.isAllowed("ESTEBANEXPLORADOR", OWNER_ID));
    }

    @Test
    void sharedPresetsStayOpenToEveryoneAndUnknownKeysStayRejected() {
        assertTrue(avatars.isAllowed("mando", 8L));
        assertTrue(avatars.isAllowed("tipoterror", OWNER_ID));
        for (String invalid : new String[] {null, "", "dragon", "MANDO", "estebanexploradora"}) {
            assertFalse(avatars.isAllowed(invalid, OWNER_ID), String.valueOf(invalid));
        }
    }

    @Test
    void withoutConfigurationNobodyMayPickTheExclusiveKey() {
        ExclusiveAvatars unconfigured = new ExclusiveAvatars("");
        assertFalse(unconfigured.isAllowed(KEY, OWNER_ID));
        assertTrue(unconfigured.isAllowed("mando", OWNER_ID));
    }

    @Test
    void ownedByListsOnlyTheOwnersKeys() {
        assertEquals(List.of(KEY), avatars.ownedBy(OWNER_ID));
        assertEquals(List.of(), avatars.ownedBy(8L));
        assertEquals(List.of(), avatars.ownedBy(null));
        assertEquals(List.of(), new ExclusiveAvatars("").ownedBy(OWNER_ID));
    }

    @Test
    void parseIgnoresUnknownKeysAndMalformedPairs() {
        assertEquals(Map.of(), ExclusiveAvatars.parse(null));
        assertEquals(Map.of(), ExclusiveAvatars.parse("  "));
        assertEquals(Map.of(KEY, 3L),
                ExclusiveAvatars.parse(" mando:1, dragon:2 ," + KEY + " : 3 ,x, " + KEY + ":abc"));
    }
}
