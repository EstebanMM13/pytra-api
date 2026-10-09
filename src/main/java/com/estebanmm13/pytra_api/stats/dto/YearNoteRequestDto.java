package com.estebanmm13.pytra_api.stats.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class YearNoteRequestDto {

    public static final int MAX_LENGTH = 10_000;

    @Size(max = MAX_LENGTH)
    private String summary;

    @Size(max = MAX_LENGTH)
    private String highlights;
}
