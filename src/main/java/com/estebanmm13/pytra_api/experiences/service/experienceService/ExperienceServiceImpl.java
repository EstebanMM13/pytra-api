package com.estebanmm13.pytra_api.experiences.service.experienceService;

import com.estebanmm13.pytra_api.diary.repository.DiaryEntryGameRepository;
import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.experiences.dto.experience.ExperiencePatchDto;
import com.estebanmm13.pytra_api.experiences.dto.experience.ExperienceRequestDto;
import com.estebanmm13.pytra_api.experiences.dto.experience.ExperienceResponseDto;
import com.estebanmm13.pytra_api.experiences.mapper.ExperienceMapper;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.service.hybridPlaytime.HybridPlaytimeReconciler;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.platforms.service.PlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class ExperienceServiceImpl implements ExperienceService {

    private final ExperienceRepository experienceRepository;
    private final GameRepository gameRepository;
    private final ExperienceMapper experienceMapper;
    private final PlatformService platformService;
    private final HybridPlaytimeReconciler hybridPlaytimeReconciler;
    private final DiaryEntryGameRepository diaryEntryGameRepository;

    @Override
    public List<ExperienceResponseDto> findAllByGame(Long gameId, Long userId) {
        requireOwnedGame(gameId, userId);

        List<Experience> experiences = experienceRepository.findAllByGameIdAndUserId(gameId, userId);
        List<ExperienceResponseDto> experienceResponseDtos = new ArrayList<>();
        for (Experience experience : experiences) {
            experienceResponseDtos.add(experienceMapper.toResponseDto(experience));
        }
        return experienceResponseDtos;
    }

    @Override
    public ExperienceResponseDto findById(Long id, Long userId) {
        Experience experience = experienceRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Experience not found"));
        return experienceMapper.toResponseDto(experience);
    }

    // Create, update and delete also move a synced HYBRID game's hours between its runs and its
    // online playtime (HybridPlaytimeReconciler), so each one is a single transaction.
    @Override
    @Transactional
    public ExperienceResponseDto create(Long gameId, ExperienceRequestDto experienceRequestDto, Long userId) {
        Game game = requireOwnedGame(gameId, userId);
        // Only new runs are refused: existing runs of a game later recategorised as ONLINE stay editable.
        if (game.getCategory() == GameCategory.ONLINE) {
            throw new InvalidRequestException(InvalidRequestException.ONLINE_GAME_RUN);
        }
        Double syncedShareBefore = hybridPlaytimeReconciler.shareBeforeRunChange(game);

        Experience experience = Experience.builder()
                .userId(userId)
                .gameId(gameId)
                .runLabel(experienceRequestDto.getRunLabel())
                .year(experienceRequestDto.getYear())
                .status(experienceRequestDto.getStatus())
                .rating(experienceRequestDto.getRating())
                .hours(experienceRequestDto.getHours() != null ? experienceRequestDto.getHours() : 0.0)
                .startDate(experienceRequestDto.getStartDate())
                .endDate(experienceRequestDto.getEndDate())
                .platform(platformService.resolve(
                        experienceRequestDto.getPlatformId(), experienceRequestDto.getPlatform(), null))
                .platinum(experienceRequestDto.getPlatinum() != null ? experienceRequestDto.getPlatinum() : false)
                .replay(experienceRequestDto.getReplay() != null ? experienceRequestDto.getReplay() : false)
                .summary(experienceRequestDto.getSummary())
                .pros(experienceRequestDto.getPros())
                .cons(experienceRequestDto.getCons())
                .notes(experienceRequestDto.getNotes())
                .build();

        experienceRepository.save(experience);
        hybridPlaytimeReconciler.afterRunChange(game, syncedShareBefore);
        return experienceMapper.toResponseDto(experience);
    }

    @Override
    @Transactional
    public ExperienceResponseDto update(Long id, ExperienceRequestDto experienceRequestDto, Long userId) {
        return saveReconciled(id, userId, experience -> {
            experience.setRunLabel(experienceRequestDto.getRunLabel());
            experience.setYear(experienceRequestDto.getYear());
            experience.setStatus(experienceRequestDto.getStatus());
            experience.setRating(experienceRequestDto.getRating());
            experience.setHours(experienceRequestDto.getHours() != null ? experienceRequestDto.getHours() : 0.0);
            experience.setStartDate(experienceRequestDto.getStartDate());
            experience.setEndDate(experienceRequestDto.getEndDate());
            experience.setPlatform(platformService.resolve(
                    experienceRequestDto.getPlatformId(), experienceRequestDto.getPlatform(), experience.getPlatform()));
            experience.setPlatinum(experienceRequestDto.getPlatinum() != null ? experienceRequestDto.getPlatinum() : false);
            experience.setReplay(experienceRequestDto.getReplay() != null ? experienceRequestDto.getReplay() : false);
            experience.setSummary(experienceRequestDto.getSummary());
            experience.setPros(experienceRequestDto.getPros());
            experience.setCons(experienceRequestDto.getCons());
            experience.setNotes(experienceRequestDto.getNotes());
        });
    }

    @Override
    @Transactional
    public ExperienceResponseDto patch(Long id, ExperiencePatchDto experiencePatchDto, Long userId) {
        return saveReconciled(id, userId, experience -> {
            if (experiencePatchDto.getHours() != null) {
                experience.setHours(experiencePatchDto.getHours());
            }
            if (experiencePatchDto.isRatingSent()) {
                experience.setRating(experiencePatchDto.getRating());
            }
        });
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        Experience experience = experienceRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Experience not found"));
        Game game = gameRepository.findByIdAndUserId(experience.getGameId(), userId).orElse(null);
        Double syncedShareBefore = hybridPlaytimeReconciler.shareBeforeRunChange(game);
        // The diary hours added to this run go away with it: its diary lines must never subtract them again.
        diaryEntryGameRepository.detachRun(experience.getId());
        experienceRepository.delete(experience);
        hybridPlaytimeReconciler.afterRunChange(game, syncedShareBefore);
    }

    @Override
    @Transactional
    public void addHours(Long id, BigDecimal deltaHours, Long userId) {
        saveReconciled(id, userId, experience -> experience.setHours(HoursArithmetic.add(experience.getHours(), deltaHours)));
    }

    /**
     * Applies {@code changes} to the user's run and saves it, moving a synced HYBRID game's hours between
     * its runs and its online playtime around the change. Shared by the full and the partial update.
     */
    private ExperienceResponseDto saveReconciled(Long id, Long userId, Consumer<Experience> changes) {
        Experience experience = experienceRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Experience not found"));
        Game game = gameRepository.findByIdAndUserId(experience.getGameId(), userId).orElse(null);
        Double syncedShareBefore = hybridPlaytimeReconciler.shareBeforeRunChange(game);

        changes.accept(experience);

        experienceRepository.save(experience);
        hybridPlaytimeReconciler.afterRunChange(game, syncedShareBefore);
        return experienceMapper.toResponseDto(experience);
    }

    private Game requireOwnedGame(Long gameId, Long userId) {
        return gameRepository.findByIdAndUserId(gameId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Game not found"));
    }
}
