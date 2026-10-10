package com.estebanmm13.pytra_api.auth.validation;

import java.util.Set;

/**
 * Single source of truth (server side) for the preset profile avatars. The web client renders each key as
 * {@code /avatars/<key>.svg} from its own catalog (src/app/shared/ui/avatar-catalog.ts), so these keys must match
 * that catalog exactly. The list is intentionally duplicated across repos; AvatarPolicyTest and the web catalog spec
 * both pin the same 48 keys so a change on one side fails a test until the other side follows.
 */
public final class AvatarPolicy {

    public static final int MAX_LENGTH = 32;

    public static final Set<String> KEYS = Set.of(
            // hardware
            "mando", "portatil", "joystick", "cartucho", "auriculares", "recreativa", "raton", "disco",
            // objetos
            "espada", "pocion", "corazon", "moneda", "llave", "cofre", "escudo", "bomba",
            // abstracto
            "barras", "play", "pixeles", "anillo", "triangulos", "ondas", "rombo", "orbita",
            // animales
            "zorro", "buho", "gato", "rana", "oso", "pulpo", "pinguino", "conejo",
            // pixel
            "heroe", "invasor", "fantasmapx", "setapx", "corazonpx", "espadapx", "slimepx", "calavera",
            // generos
            "rpg", "shooter", "carreras", "puzzle", "terror", "plataformas", "estrategia", "deportes");

    private AvatarPolicy() {
    }

    public static boolean isValid(String key) {
        return key != null && KEYS.contains(key);
    }
}
