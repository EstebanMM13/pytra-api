package com.estebanmm13.pytra_api.achievements.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Achievement ids the web computed as unlocked. The catalog lives in the web, so only the shape of
 * each id is checked: 1-64 letters, digits, '_' or '-'.
 */
@Getter
@Setter
public class RecordAchievementsRequestDto {

    public static final int MAX_IDS = 100;
    public static final int MAX_ID_LENGTH = 64;

    @NotNull
    @Size(min = 1, max = MAX_IDS)
    private List<@NotNull @Size(max = MAX_ID_LENGTH) @Pattern(regexp = "[A-Za-z0-9_-]+") String> ids;
}
