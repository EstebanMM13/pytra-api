package com.estebanmm13.pytra_api.diary.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** The whole diary day (PUT replaces it): the day's note and every game played that day. */
@Getter
@Setter
public class DiaryDayRequestDto {

    @Size(max = 1000)
    private String note;

    @NotNull
    @Size(max = 30)
    private List<@Valid @NotNull DiaryGameRequestDto> games = new ArrayList<>();
}
