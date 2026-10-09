package com.estebanmm13.pytra_api.account.export;

import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.model.Platform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.model.Saga;
import com.estebanmm13.pytra_api.stats.model.YearNote;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountExportWriterTest {

    private final Game elden = Game.builder()
            .id(1L).name("Elden Ring").developer("FromSoftware").category(GameCategory.SINGLEPLAYER)
            .saga(Saga.builder().id(5L).name("Souls").build())
            .genres(Set.of(Genre.builder().id(1L).name("RPG").build()))
            .reviewStatus(ReviewStatus.CONFIRMED).build();
    private final Game empty = Game.builder()
            .id(2L).name("Backlog game").genres(Set.of()).reviewStatus(ReviewStatus.CONFIRMED).build();

    private final Experience run = Experience.builder()
            .id(10L).gameId(1L).runLabel("First run").year(2024).status(ExperienceStatus.COMPLETADO)
            .rating(9).hours(120.5).startDate(LocalDate.of(2024, 1, 2)).endDate(LocalDate.of(2024, 3, 4))
            .platform(Platform.PC).platinum(true).replay(false)
            .summary("Loved it, \"truly\"\nsecond line").pros("=open world").build();

    private final ExportData data = new ExportData(
            "Esteban",
            LocalDateTime.of(2026, 10, 9, 12, 0),
            List.of(elden, empty),
            Map.of(1L, List.of(run)),
            Map.of(1L, OnlinePlaytime.builder().gameId(1L).totalHours(3.0).build()),
            List.of(YearNote.builder().year(2024).summary("Great year").highlights("Elden Ring").build()));

    @Test
    void csvHasOneRowPerExperienceAndOneForGamesWithoutRuns() {
        String csv = AccountExportWriter.csv(data);

        assertThat(csv).startsWith("﻿gameName,developer,");
        String[] lines = csv.substring(1).split("\r\n");
        assertThat(lines[1]).startsWith("Elden Ring,FromSoftware,,,SINGLEPLAYER,Souls,RPG,CONFIRMED,3,First run,2024,COMPLETADO,9,120.5,2024-01-02,2024-03-04,PC,true,false,");
        assertThat(csv).contains("\"Loved it, \"\"truly\"\"\nsecond line\"");
        assertThat(csv).contains("Backlog game,,,,,,,CONFIRMED,,,,,,,,,,,,,,,");
    }

    @Test
    void csvNeutralizesFormulaInjection() {
        assertThat(AccountExportWriter.csv(data)).contains(",'=open world,");
        assertThat(AccountExportWriter.text("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(AccountExportWriter.text("Plain")).isEqualTo("Plain");
    }

    @Test
    void markdownHasASectionPerGameWithRunsAndYearNotes() {
        String md = AccountExportWriter.markdown(data);

        assertThat(md).startsWith("---\nsource: pytra\nuser: Esteban\n");
        assertThat(md).contains("## Elden Ring\n");
        assertThat(md).contains("- **Saga:** [[Souls]]\n");
        assertThat(md).contains("- **Online hours:** 3\n");
        assertThat(md).contains("### First run\n");
        assertThat(md).contains("- **Rating:** 9/10\n");
        assertThat(md).contains("- **Hours:** 120.5\n");
        assertThat(md).contains("- **Platinum:** yes\n");
        assertThat(md).contains("**Summary**\n\nLoved it, \"truly\"\nsecond line\n");
        assertThat(md).contains("## Backlog game\n");
        assertThat(md).contains("_No runs logged._");
        assertThat(md).contains("# Year notes\n\n## 2024\n");
        assertThat(md).contains("**Highlights**\n\nElden Ring\n");
    }

    @Test
    void formatParamIsCaseInsensitiveAndRejectsUnknownValues() {
        assertThat(ExportFormat.fromParam("CSV")).isEqualTo(ExportFormat.CSV);
        assertThat(ExportFormat.fromParam("markdown")).isEqualTo(ExportFormat.MARKDOWN);
        assertThat(ExportFormat.fromParam("md")).isEqualTo(ExportFormat.MARKDOWN);
        assertThatThrownBy(() -> ExportFormat.fromParam("pdf"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage(InvalidRequestException.INVALID_EXPORT_FORMAT);
    }
}
