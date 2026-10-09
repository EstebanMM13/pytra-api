package com.estebanmm13.pytra_api.account.export;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.stats.model.YearNote;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Renders an {@link ExportData} as CSV or Obsidian-friendly Markdown. Pure, no I/O. */
public final class AccountExportWriter {

    static final List<String> CSV_HEADER = List.of(
            "gameName", "developer", "publisher", "releaseDate", "category", "saga", "genres", "reviewStatus",
            "onlineHours", "runLabel", "year", "status", "rating", "hours", "startDate", "endDate", "platform",
            "platinum", "replay", "summary", "pros", "cons", "notes");

    /** Lets Excel detect UTF-8 (accents in game names). */
    private static final String UTF8_BOM = "﻿";
    private static final String CRLF = "\r\n";

    private AccountExportWriter() {
    }

    public static byte[] write(ExportData data, ExportFormat format) {
        String text = format == ExportFormat.CSV ? csv(data) : markdown(data);
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * RFC 4180 CSV, one row per experience with the game's columns repeated; a game without
     * experiences still gets one row with empty run columns, so the whole library is exported.
     */
    static String csv(ExportData data) {
        StringBuilder out = new StringBuilder(UTF8_BOM);
        appendCsvRow(out, CSV_HEADER);
        for (Game game : data.games()) {
            List<String> gameColumns = List.of(
                    text(game.getName()),
                    text(game.getDeveloper()),
                    text(game.getPublisher()),
                    value(game.getReleaseDate()),
                    value(game.getCategory()),
                    text(game.getSaga() != null ? game.getSaga().getName() : null),
                    text(genreNames(game)),
                    value(game.getReviewStatus()),
                    number(onlineHours(data, game)));
            List<Experience> runs = data.experiencesByGame().getOrDefault(game.getId(), List.of());
            if (runs.isEmpty()) {
                List<String> row = new ArrayList<>(gameColumns);
                while (row.size() < CSV_HEADER.size()) row.add("");
                appendCsvRow(out, row);
            }
            for (Experience run : runs) {
                List<String> row = new ArrayList<>(gameColumns);
                row.addAll(List.of(
                        text(run.getRunLabel()),
                        value(run.getYear()),
                        value(run.getStatus()),
                        value(run.getRating()),
                        number(run.getHours()),
                        value(run.getStartDate()),
                        value(run.getEndDate()),
                        value(run.getPlatform()),
                        value(run.getPlatinum()),
                        value(run.getReplay()),
                        text(run.getSummary()),
                        text(run.getPros()),
                        text(run.getCons()),
                        text(run.getNotes())));
                appendCsvRow(out, row);
            }
        }
        return out.toString();
    }

    /** One section per game with its runs, then one section per year with the yearly notes. */
    static String markdown(ExportData data) {
        StringBuilder out = new StringBuilder();
        out.append("---\n")
                .append("source: pytra\n")
                .append("user: ").append(data.username()).append('\n')
                .append("exported_at: ").append(data.exportedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append('\n')
                .append("games: ").append(data.games().size()).append('\n')
                .append("---\n\n")
                .append("# Pytra export\n");

        for (Game game : data.games()) {
            out.append("\n## ").append(heading(game.getName())).append("\n\n");
            field(out, "Developer", game.getDeveloper());
            field(out, "Publisher", game.getPublisher());
            field(out, "Release date", value(game.getReleaseDate()));
            field(out, "Category", value(game.getCategory()));
            field(out, "Saga", game.getSaga() != null ? "[[" + game.getSaga().getName() + "]]" : null);
            field(out, "Genres", genreNames(game));
            Double online = onlineHours(data, game);
            field(out, "Online hours", online != null ? number(online) : null);

            List<Experience> runs = data.experiencesByGame().getOrDefault(game.getId(), List.of());
            if (runs.isEmpty()) {
                out.append("\n_No runs logged._\n");
            }
            for (Experience run : runs) {
                out.append("\n### ").append(heading(run.getRunLabel())).append("\n\n");
                field(out, "Status", value(run.getStatus()));
                field(out, "Rating", run.getRating() != null ? run.getRating() + "/10" : null);
                field(out, "Hours", number(run.getHours()));
                field(out, "Year", value(run.getYear()));
                field(out, "Start", value(run.getStartDate()));
                field(out, "End", value(run.getEndDate()));
                field(out, "Platform", value(run.getPlatform()));
                field(out, "Platinum", Boolean.TRUE.equals(run.getPlatinum()) ? "yes" : null);
                field(out, "Replay", Boolean.TRUE.equals(run.getReplay()) ? "yes" : null);
                block(out, "Summary", run.getSummary());
                block(out, "Pros", run.getPros());
                block(out, "Cons", run.getCons());
                block(out, "Notes", run.getNotes());
            }
        }

        if (!data.yearNotes().isEmpty()) {
            out.append("\n# Year notes\n");
            data.yearNotes().stream()
                    .sorted(Comparator.comparing(YearNote::getYear).reversed())
                    .forEach(note -> {
                        out.append("\n## ").append(note.getYear()).append('\n');
                        block(out, "Summary", note.getSummary());
                        block(out, "Highlights", note.getHighlights());
                    });
        }
        return out.toString();
    }

    private static void appendCsvRow(StringBuilder out, List<String> values) {
        out.append(values.stream().map(AccountExportWriter::csvEscape).collect(Collectors.joining(","))).append(CRLF);
    }

    static String csvEscape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        return needsQuotes ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    /**
     * Free text typed by the user. A leading {@code = + - @} (or tab/CR) is prefixed with a quote so a
     * spreadsheet never evaluates it as a formula (CSV injection).
     */
    static String text(String value) {
        if (value == null) {
            return "";
        }
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }

    /** 42.5 -> "42.5", 10.0 -> "10", never scientific notation. */
    static String number(Double value) {
        return value == null ? "" : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static Double onlineHours(ExportData data, Game game) {
        OnlinePlaytime online = data.onlinePlaytimeByGame().get(game.getId());
        return online != null ? online.getTotalHours() : null;
    }

    private static String genreNames(Game game) {
        if (game.getGenres() == null || game.getGenres().isEmpty()) {
            return null;
        }
        return game.getGenres().stream()
                .map(Genre::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
    }

    private static String heading(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static void field(StringBuilder out, String label, String value) {
        if (value != null && !value.isBlank()) {
            out.append("- **").append(label).append(":** ").append(value).append('\n');
        }
    }

    private static void block(StringBuilder out, String label, String value) {
        if (value != null && !value.isBlank()) {
            out.append("\n**").append(label).append("**\n\n").append(value.strip()).append('\n');
        }
    }
}
