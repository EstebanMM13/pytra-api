package com.estebanmm13.pytra_api.auth.validation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvatarPolicyTest {

    /**
     * Mirrors the web catalog (pytra-web src/app/shared/ui/avatar-catalog.ts, pinned by avatar-catalog.spec.ts).
     * The two repos cannot share a file, so both sides assert this exact list: changing one without the other
     * fails a test.
     */
    private static final List<String> EXPECTED_KEYS = List.of(
            "mando", "portatil", "joystick", "cartucho", "auriculares", "recreativa", "raton", "disco",
            "espada", "pocion", "corazon", "moneda", "llave", "cofre", "escudo", "bomba",
            "barras", "play", "pixeles", "anillo", "triangulos", "ondas", "rombo", "orbita",
            "zorro", "buho", "gato", "rana", "oso", "pulpo", "pinguino", "conejo",
            "heroe", "invasor", "fantasmapx", "setapx", "corazonpx", "espadapx", "slimepx", "calavera",
            "rpg", "shooter", "carreras", "puzzle", "terror", "plataformas", "estrategia", "deportes");

    @Test
    void keysAreExactlyTheIllustratedPack() {
        assertEquals(48, EXPECTED_KEYS.size());
        assertEquals(Set.copyOf(EXPECTED_KEYS), AvatarPolicy.KEYS);
    }

    @Test
    void everyKeyFitsTheColumnAndIsLowercaseAscii() {
        for (String key : AvatarPolicy.KEYS) {
            assertTrue(key.length() <= AvatarPolicy.MAX_LENGTH, key);
            assertTrue(key.matches("[a-z]+"), key);
        }
    }

    @Test
    void isValidRejectsOldKeysAndOddInput() {
        assertTrue(AvatarPolicy.isValid("mando"));
        for (String invalid : new String[] {null, "", "gamepad", "ghost", "crown", "MANDO", " mando"}) {
            assertFalse(AvatarPolicy.isValid(invalid), String.valueOf(invalid));
        }
    }
}
