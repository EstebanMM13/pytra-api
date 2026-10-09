package com.estebanmm13.pytra_api.account.service;

import com.estebanmm13.pytra_api.account.export.AccountExportWriter;
import com.estebanmm13.pytra_api.account.export.ExportData;
import com.estebanmm13.pytra_api.account.export.ExportFile;
import com.estebanmm13.pytra_api.account.export.ExportFormat;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.auth.repository.UserRepository;
import com.estebanmm13.pytra_api.auth.validation.UsernamePolicy;
import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperiencePeriod;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.stats.repository.YearNoteRepository;
import com.estebanmm13.pytra_api.steamsync.service.SteamSyncGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AccountServiceImpl implements AccountService {

    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final ExperienceRepository experienceRepository;
    private final OnlinePlaytimeRepository onlinePlaytimeRepository;
    private final YearNoteRepository yearNoteRepository;
    private final SteamSyncGuard steamSyncGuard;
    private final TransactionTemplate transactionTemplate;

    public AccountServiceImpl(UserRepository userRepository,
                              GameRepository gameRepository,
                              ExperienceRepository experienceRepository,
                              OnlinePlaytimeRepository onlinePlaytimeRepository,
                              YearNoteRepository yearNoteRepository,
                              SteamSyncGuard steamSyncGuard,
                              PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
        this.experienceRepository = experienceRepository;
        this.onlinePlaytimeRepository = onlinePlaytimeRepository;
        this.yearNoteRepository = yearNoteRepository;
        this.steamSyncGuard = steamSyncGuard;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional(readOnly = true)
    public ExportFile export(Long userId, String format) {
        ExportFormat exportFormat = ExportFormat.fromParam(format);
        User user = requireUser(userId);

        List<Game> games = new ArrayList<>(gameRepository.findAllByUserIdWithSagaAndGenres(userId));
        games.sort(Comparator.comparing(Game::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(Game::getId));

        Map<Long, List<Experience>> experiencesByGame = new HashMap<>();
        for (Experience experience : experienceRepository.findAllByUserId(userId)) {
            experiencesByGame.computeIfAbsent(experience.getGameId(), id -> new ArrayList<>()).add(experience);
        }
        // Oldest run first inside each game, like a diary.
        experiencesByGame.values().forEach(runs -> runs.sort(ExperiencePeriod.RECENCY));

        Map<Long, OnlinePlaytime> onlineByGame = new HashMap<>();
        for (OnlinePlaytime online : onlinePlaytimeRepository.findAllByUserIdOrderByTotalHoursDesc(userId)) {
            onlineByGame.put(online.getGameId(), online);
        }

        ExportData data = new ExportData(
                user.getUsernameDisplay(),
                LocalDateTime.now(),
                games,
                experiencesByGame,
                onlineByGame,
                yearNoteRepository.findAllByUserIdOrderByYearDesc(userId));

        String filename = "pytra-export-" + user.getUsername() + "-" + LocalDate.now() + "." + exportFormat.extension();
        return new ExportFile(filename, exportFormat.mediaType(), AccountExportWriter.write(data, exportFormat));
    }

    /**
     * Every user-owned table references users(id) ON DELETE CASCADE (tokens, sagas, games and their
     * genre links, experiences, online playtime, Steam links/states/ignored apps/platform links,
     * year notes), so deleting the user row removes all of it atomically in this one transaction.
     * The global genre catalog is not user-owned and stays.
     */
    @Override
    public void deleteAccount(Long userId, String confirm) {
        User user = requireUser(userId);
        if (confirm == null || !UsernamePolicy.normalize(confirm).equals(user.getUsername())) {
            throw new InvalidRequestException(InvalidRequestException.CONFIRMATION_MISMATCH);
        }
        // Same per-user slot as Steam sync/unlink, held until commit, so a running sync cannot
        // write rows for a user that is being deleted (answers 409 SYNC_IN_PROGRESS instead).
        steamSyncGuard.runExclusive(userId, () -> transactionTemplate.execute(status -> {
            userRepository.findById(userId).ifPresent(userRepository::delete);
            return null;
        }));
        log.info("Account {} deleted", userId);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
