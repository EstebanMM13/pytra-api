package com.estebanmm13.pytra_api.account.export;

import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.platforms.TestPlatforms;
import com.estebanmm13.pytra_api.platforms.model.Platform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.model.Saga;
import com.estebanmm13.pytra_api.stats.model.YearNote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
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
            .rating(new BigDecimal("9.00")).hours(120.5).startDate(LocalDate.of(2024, 1, 2)).endDate(LocalDate.of(2024, 3, 4))
            .platform(TestPlatforms.PC).platinum(true).fullCompletion(true).replay(false)
            .summary("Loved it, \"truly\"\nsecond line").pros("=open world").build();

    private final ExportData data = new ExportData(
            "Esteban",
            LocalDateTime.of(2026, 10, 9, 12, 0),
            List.of(elden, empty),
            Map.of(1L, List.of(run)),
            Map.of(1L, OnlinePlaytime.builder().gameId(1L).totalHours(3.0).fullCompletion(true)
                    .platforms(new HashSet<>(Set.of(TestPlatforms.PS5, TestPlatforms.PC))).build()),
            List.of(YearNote.builder().year(2024).summary("Great year").highlights("Elden Ring").build()));

    @Test
    void csvHasOneRowPerExperienceAndOneForGamesWithoutRuns() {
        String csv = AccountExportWriter.csv(data);

        assertThat(csv).startsWith("﻿gameName,developer,");
        String[] lines = csv.substring(1).split("\r\n");
        assertThat(lines[1]).startsWith("Elden Ring,FromSoftware,,,SINGLEPLAYER,Souls,RPG,CONFIRMED,3,\"PC, PS5\",false,true,First run,2024,COMPLETADO,9,120.5,2024-01-02,2024-03-04,PC,true,true,false,");
        assertThat(csv).contains("\"Loved it, \"\"truly\"\"\nsecond line\"");
        assertThat(csv).contains("Backlog game,,,,,,,CONFIRMED,,,,,,,,,,,,,,,,");
    }

    @Test
    void platformIsExportedByCatalogNameAndNeutralized() {
        Experience handheld = Experience.builder()
                .id(11L).gameId(1L).runLabel("Handheld").status(ExperienceStatus.COMPLETADO).hours(1.0)
                .platform(TestPlatforms.DS).platinum(false).fullCompletion(false).replay(false).build();
        Experience injected = Experience.builder()
                .id(12L).gameId(1L).runLabel("Injected").status(ExperienceStatus.COMPLETADO).hours(1.0)
                .platform(Platform.builder().id(99L).name("=HYPERLINK(1)").sortOrder(1000).build())
                .platinum(false).fullCompletion(false).replay(false).build();
        ExportData withPlatforms = new ExportData("Esteban", LocalDateTime.of(2026, 10, 9, 12, 0), List.of(elden),
                Map.of(1L, List.of(handheld, injected)), Map.of(), List.of());

        String csv = AccountExportWriter.csv(withPlatforms);
        assertThat(csv).contains(",DS,false,false,false,");
        assertThat(csv).contains(",'=HYPERLINK(1),false,false,false,");
        assertThat(AccountExportWriter.markdown(withPlatforms)).contains("DS");
    }

    @Test
    void csvNeutralizesFormulaInjection() {
        assertThat(AccountExportWriter.csv(data)).contains(",'=open world,");
        assertThat(AccountExportWriter.text("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(AccountExportWriter.text("Plain")).isEqualTo("Plain");
        assertThat(AccountExportWriter.text("")).isEmpty();
        assertThat(AccountExportWriter.text("   ")).isEqualTo("   ");
        assertThat(AccountExportWriter.text("Half-Life 2")).isEqualTo("Half-Life 2");
    }

    @Test
    void csvNeutralizesFormulasHiddenBehindWhitespaceOrControlCharacters() {
        assertThat(AccountExportWriter.text(" =1+1")).isEqualTo("' =1+1");
        assertThat(AccountExportWriter.text("\n=1+1")).isEqualTo("'\n=1+1");
        assertThat(AccountExportWriter.text("\t@x")).isEqualTo("'\t@x");
        assertThat(AccountExportWriter.text("\u0000plain")).isEqualTo("'\u0000plain");
        assertThat(AccountExportWriter.text("\rtext")).isEqualTo("'\rtext");
        assertThat(AccountExportWriter.text(" \u0001 -2")).isEqualTo("' \u0001 -2");
        assertThat(AccountExportWriter.text(" +cmd")).isEqualTo("' +cmd");
        assertThat(AccountExportWriter.text("  spaced text")).isEqualTo("  spaced text");
    }

    @Test
    void markdownFrontMatterAndSagaLinksStaySingleLineAndEscaped() {
        Game game = Game.builder().id(3L).name("G").genres(Set.of())
                .saga(Saga.builder().id(9L).name("Evil\r\nsaga").build())
                .reviewStatus(ReviewStatus.CONFIRMED).build();
        ExportData tricky = new ExportData("bad\"name\\\ninjected: true", LocalDateTime.of(2026, 1, 1, 0, 0),
                List.of(game), Map.of(), Map.of(), List.of());

        String md = AccountExportWriter.markdown(tricky);

        assertThat(md).contains("user: \"bad\\\"name\\\\ injected: true\"\n");
        assertThat(md).doesNotContain("\ninjected: true");
        assertThat(md).contains("- **Saga:** [[Evil saga]]\n");
        assertThat(AccountExportWriter.yamlString(null)).isEqualTo("\"\"");
    }

    @Test
    void markdownHasASectionPerGameWithRunsAndYearNotes() {
        String md = AccountExportWriter.markdown(data);

        assertThat(md).startsWith("---\nsource: pytra\nuser: \"Esteban\"\n");
        assertThat(md).contains("## Elden Ring\n");
        assertThat(md).contains("- **Saga:** [[Souls]]\n");
        assertThat(md).contains("- **Online hours:** 3\n");
        assertThat(md).contains("- **Online platforms:** PC, PS5\n");
        assertThat(md).contains("- **Online 100%:** yes\n");
        assertThat(md).doesNotContain("Online platinum");
        assertThat(md).contains("### First run\n");
        assertThat(md).contains("- **Rating:** 9/10\n");
        assertThat(md).contains("- **Hours:** 120.5\n");
        assertThat(md).contains("- **Platinum:** yes\n");
        assertThat(md).contains("- **100%:** yes\n");
        assertThat(md).contains("**Summary**\n\nLoved it, \"truly\"\nsecond line\n");
        assertThat(md).contains("## Backlog game\n");
        assertThat(md).contains("_No runs logged._");
        assertThat(md).contains("# Year notes\n\n## 2024\n");
        assertThat(md).contains("**Highlights**\n\nElden Ring\n");
    }

    @Test
    void ratingsAreExportedWithoutTrailingZeros() {
        assertThat(AccountExportWriter.rating(new BigDecimal("9.00"))).isEqualTo("9");
        assertThat(AccountExportWriter.rating(new BigDecimal("9.50"))).isEqualTo("9.5");
        assertThat(AccountExportWriter.rating(new BigDecimal("9.25"))).isEqualTo("9.25");
        assertThat(AccountExportWriter.rating(new BigDecimal("10.00"))).isEqualTo("10");
        assertThat(AccountExportWriter.rating(new BigDecimal("0.00"))).isEqualTo("0");
        assertThat(AccountExportWriter.rating(null)).isEmpty();
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
