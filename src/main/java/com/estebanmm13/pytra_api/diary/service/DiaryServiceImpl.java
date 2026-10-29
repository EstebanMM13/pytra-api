package com.estebanmm13.pytra_api.diary.service;

import com.estebanmm13.pytra_api.diary.dto.DiaryDayRequestDto;
import com.estebanmm13.pytra_api.diary.dto.DiaryDayResponseDto;
import com.estebanmm13.pytra_api.diary.dto.DiaryGameRequestDto;
import com.estebanmm13.pytra_api.diary.dto.DiaryGameResponseDto;
import com.estebanmm13.pytra_api.diary.dto.GameDiaryDayResponseDto;
import com.estebanmm13.pytra_api.diary.model.DiaryEntry;
import com.estebanmm13.pytra_api.diary.model.DiaryEntryGame;
import com.estebanmm13.pytra_api.diary.repository.DiaryEntryGameRepository;
import com.estebanmm13.pytra_api.diary.repository.DiaryEntryRepository;
import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.service.experienceService.ExperienceService;
import com.estebanmm13.pytra_api.experiences.service.onlinePlaytimeService.OnlinePlaytimeService;
import com.estebanmm13.pytra_api.experiences.validation.RunDateLimits;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The play diary. Hours of a diary game are ADDED to the game's data, inside the same transaction:
 * <ul>
 *   <li>ONLINE games: to the online playtime (row created when missing).</li>
 *   <li>Steam/PSN-synced games: not added at all (the sync counts the real playtime); kept in the diary only.
 *   A line already counted before the game was linked keeps counting.</li>
 *   <li>Other games: to one run: the one sent, else the one the line already used, else the only run in
 *   progress; otherwise 400 {@value InvalidRequestException#DIARY_RUN_REQUIRED}.</li>
 * </ul>
 * Editing applies only the difference, moving the hours when the target changes; deleting subtracts. Run
 * hours change through {@link ExperienceService#addHours} (the reconciled run save) and online hours through
 * {@link OnlinePlaytimeService#addHours}, both clamped at 0.
 */
@Service
@RequiredArgsConstructor
public class DiaryServiceImpl implements DiaryService {

    static final Set<ExternalPlatform> SYNCED_PLATFORMS =
            EnumSet.of(ExternalPlatform.STEAM, ExternalPlatform.PSN, ExternalPlatform.PSN_PS4, ExternalPlatform.PSN_PS5);
    static final int MAX_RANGE_DAYS = 400;

    private final DiaryEntryRepository diaryEntryRepository;
    private final DiaryEntryGameRepository diaryEntryGameRepository;
    private final GameRepository gameRepository;
    private final ExperienceRepository experienceRepository;
    private final GamePlatformLinkRepository gamePlatformLinkRepository;
    private final ExperienceService experienceService;
    private final OnlinePlaytimeService onlinePlaytimeService;

    /** Where a line's hours were added: a run, or the online playtime ({@code experienceId == null}). */
    private record Target(Long experienceId) {
        static final Target ONLINE = new Target(null);
    }

    /**
     * A requested game once validated: where its hours go (null = nowhere), the run to remember and whether
     * the line stays a sync-written auto line.
     */
    private record ResolvedGame(DiaryGameRequestDto request, Target target, Long experienceId, boolean autoSynced) {
        ResolvedGame(DiaryGameRequestDto request, Target target, Long experienceId) {
            this(request, target, experienceId, false);
        }
    }

    @Override
    public List<DiaryDayResponseDto> findRange(LocalDate from, LocalDate to, Long userId) {
        if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new InvalidRequestException(InvalidRequestException.INVALID_DIARY_RANGE);
        }
        List<DiaryEntry> entries = diaryEntryRepository
                .findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, from, to).stream()
                .filter(entry -> !entry.getGames().isEmpty() || entry.getNote() != null)
                .toList();
        return toResponses(entries, userId);
    }

    @Override
    public DiaryDayResponseDto findDay(LocalDate date, Long userId) {
        DiaryEntry entry = diaryEntryRepository.findByUserIdAndEntryDate(userId, date)
                .orElseThrow(() -> new ResourceNotFoundException("Diary entry not found"));
        return toResponses(List.of(entry), userId).get(0);
    }

    @Override
    @Transactional
    public Optional<DiaryDayResponseDto> saveDay(LocalDate date, DiaryDayRequestDto request, Long userId) {
        requireValidDate(date);
        List<DiaryGameRequestDto> requested = request.getGames() == null ? List.of() : request.getGames();
        String note = trimToNull(request.getNote());
        requireDistinctGames(requested);

        Optional<DiaryEntry> existing = diaryEntryRepository.findByUserIdAndEntryDate(userId, date);
        if (requested.isEmpty() && note == null) {
            existing.ifPresent(entry -> removeEntry(entry, userId));
            return Optional.empty();
        }

        Map<Long, DiaryEntryGame> oldLines = existing.map(entry -> entry.getGames().stream()
                        .collect(Collectors.toMap(DiaryEntryGame::getGameId, Function.identity())))
                .orElseGet(Map::of);
        // Everything is validated before any hour moves, so a refused request changes nothing.
        List<ResolvedGame> resolved = resolveGames(requested, oldLines, userId);
        Set<Long> requestedIds = requested.stream().map(DiaryGameRequestDto::getGameId).collect(Collectors.toSet());

        for (DiaryEntryGame old : oldLines.values()) {
            if (!requestedIds.contains(old.getGameId())) {
                moveHours(old.getGameId(), old, null, BigDecimal.ZERO, userId);
            }
        }
        for (ResolvedGame game : resolved) {
            Long gameId = game.request().getGameId();
            moveHours(gameId, oldLines.get(gameId), game.target(), game.request().getHours(), userId);
        }

        DiaryEntry entry = existing.orElseGet(() -> DiaryEntry.builder().userId(userId).entryDate(date).build());
        entry.setNote(note);
        writeLines(entry, resolved);
        diaryEntryRepository.save(entry);
        return Optional.of(toResponses(List.of(entry), userId).get(0));
    }

    @Override
    @Transactional
    public void deleteDay(LocalDate date, Long userId) {
        DiaryEntry entry = diaryEntryRepository.findByUserIdAndEntryDate(userId, date)
                .orElseThrow(() -> new ResourceNotFoundException("Diary entry not found"));
        removeEntry(entry, userId);
    }

    @Override
    public List<GameDiaryDayResponseDto> findByGame(Long gameId, Long userId) {
        requireOwnedGame(gameId, userId);
        return diaryEntryGameRepository.findAllByUserIdAndGameIdLatestFirst(userId, gameId).stream()
                .map(line -> new GameDiaryDayResponseDto(line.getEntry().getEntryDate(), line.getHours(),
                        line.getNote(), line.getExperienceId(), line.isCountsHours(), line.isAutoSynced()))
                .toList();
    }

    private List<ResolvedGame> resolveGames(List<DiaryGameRequestDto> requested, Map<Long, DiaryEntryGame> oldLines,
                                            Long userId) {
        Set<Long> syncedGameIds = syncedGameIds(userId);
        List<ResolvedGame> resolved = new ArrayList<>();
        Map<String, String> runErrors = new LinkedHashMap<>();
        for (int i = 0; i < requested.size(); i++) {
            DiaryGameRequestDto game = requested.get(i);
            Game owned = requireOwnedGame(game.getGameId(), userId);
            String field = "games[" + i + "].experienceId";
            DiaryEntryGame oldLine = oldLines.get(game.getGameId());

            // A sync-written line never counts, even once edited or after the game was unlinked: the sync
            // already added its hours. It stays "auto" only while the user leaves its hours and run as they are
            // (the web sends the whole day back on every save); any change makes it the user's line.
            if (oldLine != null && oldLine.isAutoSynced()) {
                Long run = game.getExperienceId() != null ? game.getExperienceId() : oldLine.getExperienceId();
                if (game.getExperienceId() != null && !isRunOf(run, game.getGameId(), userId)) {
                    runErrors.put(field, InvalidRequestException.INVALID_DIARY_RUN);
                }
                boolean unchanged = sameDiaryHours(game.getHours(), oldLine.getHours())
                        && Objects.equals(run, oldLine.getExperienceId());
                resolved.add(new ResolvedGame(game, null, run, unchanged));
                continue;
            }

            // A line counted before the game was linked keeps counting: the link absorbed those hours
            // (max on confirm, then only later deltas), so giving them back would lose them.
            if (syncedGameIds.contains(game.getGameId()) && (oldLine == null || !oldLine.isCountsHours())) {
                Long run = game.getExperienceId();
                if (run != null && !isRunOf(run, game.getGameId(), userId)) {
                    runErrors.put(field, InvalidRequestException.INVALID_DIARY_RUN);
                }
                resolved.add(new ResolvedGame(game, null, run));
                continue;
            }
            if (owned.getCategory() == GameCategory.ONLINE) {
                resolved.add(new ResolvedGame(game, Target.ONLINE, null));
                continue;
            }

            List<Experience> runs = experienceRepository.findAllByGameIdAndUserId(game.getGameId(), userId);
            Long run = pickRun(game, oldLine, runs);
            if (run == null) {
                runErrors.put(field, game.getExperienceId() != null
                        ? InvalidRequestException.INVALID_DIARY_RUN
                        : InvalidRequestException.DIARY_RUN_REQUIRED);
            }
            resolved.add(new ResolvedGame(game, new Target(run), run));
        }
        if (!runErrors.isEmpty()) {
            String code = runErrors.containsValue(InvalidRequestException.DIARY_RUN_REQUIRED)
                    ? InvalidRequestException.DIARY_RUN_REQUIRED
                    : InvalidRequestException.INVALID_DIARY_RUN;
            throw new InvalidRequestException(code, runErrors);
        }
        return resolved;
    }

    /** The sent run, else the run the line already used, else the only run in progress; null when none fits. */
    private static Long pickRun(DiaryGameRequestDto game, DiaryEntryGame oldLine, List<Experience> runs) {
        Set<Long> runIds = runs.stream().map(Experience::getId).collect(Collectors.toSet());
        if (game.getExperienceId() != null) {
            return runIds.contains(game.getExperienceId()) ? game.getExperienceId() : null;
        }
        if (oldLine != null && oldLine.getExperienceId() != null && runIds.contains(oldLine.getExperienceId())) {
            return oldLine.getExperienceId();
        }
        List<Experience> inProgress = runs.stream().filter(run -> run.getStatus() == ExperienceStatus.EN_CURSO).toList();
        return inProgress.size() == 1 ? inProgress.get(0).getId() : null;
    }

    private boolean isRunOf(Long experienceId, Long gameId, Long userId) {
        return experienceRepository.findByIdAndUserId(experienceId, userId)
                .filter(run -> gameId.equals(run.getGameId()))
                .isPresent();
    }

    /**
     * Moves the game data from what {@code old} added to {@code newHours} on {@code newTarget} (null = add
     * nothing): only the difference on the same target; otherwise subtract from the old one, add to the new one.
     */
    private void moveHours(Long gameId, DiaryEntryGame old, Target newTarget, BigDecimal newHours, Long userId) {
        Target oldTarget = old != null && old.isCountsHours() ? new Target(old.getExperienceId()) : null;
        BigDecimal oldHours = oldTarget != null ? old.getHours() : BigDecimal.ZERO;

        if (Objects.equals(oldTarget, newTarget)) {
            BigDecimal delta = newHours.subtract(oldHours);
            if (newTarget != null && delta.signum() != 0) {
                addHours(newTarget, gameId, delta, userId);
            }
            return;
        }
        if (oldTarget != null) {
            addHours(oldTarget, gameId, oldHours.negate(), userId);
        }
        if (newTarget != null && newHours.signum() != 0) {
            addHours(newTarget, gameId, newHours, userId);
        }
    }

    private void addHours(Target target, Long gameId, BigDecimal delta, Long userId) {
        if (target.experienceId() != null) {
            experienceService.addHours(target.experienceId(), delta, userId);
        } else {
            onlinePlaytimeService.addHours(gameId, delta, userId);
        }
    }

    /** Updates the lines in place (a game keeps its row), drops the removed games and adds the new ones. */
    private static void writeLines(DiaryEntry entry, List<ResolvedGame> resolved) {
        Map<Long, DiaryEntryGame> current = new HashMap<>();
        entry.getGames().forEach(line -> current.put(line.getGameId(), line));
        Set<Long> kept = new HashSet<>();
        for (ResolvedGame game : resolved) {
            DiaryGameRequestDto request = game.request();
            DiaryEntryGame line = current.get(request.getGameId());
            if (line == null) {
                line = DiaryEntryGame.builder().entry(entry).gameId(request.getGameId()).build();
                entry.getGames().add(line);
            }
            line.setExperienceId(game.experienceId());
            line.setHours(request.getHours());
            line.setNote(trimToNull(request.getNote()));
            line.setCountsHours(game.target() != null);
            line.setAutoSynced(game.autoSynced());
            kept.add(request.getGameId());
        }
        entry.getGames().removeIf(line -> !kept.contains(line.getGameId()));
    }

    private void removeEntry(DiaryEntry entry, Long userId) {
        entry.getGames().forEach(line -> moveHours(line.getGameId(), line, null, BigDecimal.ZERO, userId));
        diaryEntryRepository.delete(entry);
    }

    private List<DiaryDayResponseDto> toResponses(List<DiaryEntry> entries, Long userId) {
        Set<Long> gameIds = new HashSet<>();
        Set<Long> runIds = new HashSet<>();
        for (DiaryEntry entry : entries) {
            for (DiaryEntryGame line : entry.getGames()) {
                gameIds.add(line.getGameId());
                if (line.getExperienceId() != null) {
                    runIds.add(line.getExperienceId());
                }
            }
        }
        Map<Long, Game> games = gameIds.isEmpty() ? Map.of() : gameRepository.findAllById(gameIds).stream()
                .filter(game -> userId.equals(game.getUserId()))
                .collect(Collectors.toMap(Game::getId, Function.identity()));
        Map<Long, String> runLabels = runIds.isEmpty() ? Map.of() : experienceRepository.findAllById(runIds).stream()
                .filter(run -> userId.equals(run.getUserId()))
                .collect(Collectors.toMap(Experience::getId, Experience::getRunLabel));
        Set<Long> synced = gameIds.isEmpty() ? Set.of() : syncedGameIds(userId);

        List<DiaryDayResponseDto> responses = new ArrayList<>();
        for (DiaryEntry entry : entries) {
            List<DiaryGameResponseDto> lines = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (DiaryEntryGame line : entry.getGames()) {
                Game game = games.get(line.getGameId());
                lines.add(new DiaryGameResponseDto(
                        line.getGameId(),
                        game != null ? game.getName() : null,
                        game != null ? game.getCoverImageUrl() : null,
                        line.getExperienceId(),
                        line.getExperienceId() != null ? runLabels.get(line.getExperienceId()) : null,
                        line.getHours(),
                        line.getNote(),
                        line.isCountsHours(),
                        synced.contains(line.getGameId()),
                        line.isAutoSynced()));
                total = total.add(line.getHours());
            }
            responses.add(new DiaryDayResponseDto(entry.getEntryDate(), entry.getNote(), total, lines));
        }
        return responses;
    }

    private Set<Long> syncedGameIds(Long userId) {
        Set<Long> ids = gamePlatformLinkRepository.findGameIdsByUserIdAndPlatformIn(userId, SYNCED_PLATFORMS);
        return ids == null ? Set.of() : ids;
    }

    private Game requireOwnedGame(Long gameId, Long userId) {
        return gameRepository.findByIdAndUserId(gameId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Game not found"));
    }

    private static void requireValidDate(LocalDate date) {
        if (date == null || !RunDateLimits.isWithin(date)) {
            throw new InvalidRequestException(InvalidRequestException.INVALID_DIARY_DATE);
        }
    }

    private static void requireDistinctGames(Collection<DiaryGameRequestDto> games) {
        Set<Long> seen = new HashSet<>();
        for (DiaryGameRequestDto game : games) {
            if (!seen.add(game.getGameId())) {
                throw new InvalidRequestException(InvalidRequestException.DUPLICATE_DIARY_GAME);
            }
        }
    }

    /**
     * Same hours as stored (NUMERIC(5,2)), whatever the scale sent: the web echoes 1.50 back as 1.5, and a
     * value is compared at the 2 decimals the column keeps.
     */
    static boolean sameDiaryHours(BigDecimal sent, BigDecimal stored) {
        return sent.setScale(2, RoundingMode.HALF_UP).compareTo(stored.setScale(2, RoundingMode.HALF_UP)) == 0;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
