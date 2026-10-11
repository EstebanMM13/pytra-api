package com.estebanmm13.pytra_api.platforms.model;

/** Hardware family of a platform, used to group the catalog and to pick a default color. */
public enum PlatformFamily {
    PLAYSTATION("#4F7BE8"),
    XBOX("#4CA64C"),
    NINTENDO("#E0524C"),
    PC("#8A857C"),
    MOBILE("#C2873A"),
    OTHER("#9B7BD4");

    private final String defaultColor;

    PlatformFamily(String defaultColor) {
        this.defaultColor = defaultColor;
    }

    public String defaultColor() {
        return defaultColor;
    }
}
