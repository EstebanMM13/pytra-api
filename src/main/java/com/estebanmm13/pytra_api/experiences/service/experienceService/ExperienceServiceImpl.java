package com.estebanmm13.pytra_api.experiences.service.experienceService;

import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.experiences.dto.experience.ExperienceRequestDto;
import com.estebanmm13.pytra_api.experiences.dto.experience.ExperienceResponseDto;
import com.estebanmm13.pytra_api.experiences.mapper.ExperienceMapper;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExperienceServiceImpl implements ExperienceService {

    private final ExperienceRepository experienceRepository;
    private final GameRepository gameRepository;
    private final ExperienceMapper experienceMapper;

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

    @Override
    public ExperienceResponseDto create(Long gameId, ExperienceRequestDto experienceRequestDto, Long userId) {
        requireOwnedGame(gameId, userId);

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
                .platform(experienceRequestDto.getPlatform())
                .platinum(experienceRequestDto.getPlatinum() != null ? experienceRequestDto.getPlatinum() : false)
                .replay(experienceRequestDto.getReplay() != null ? experienceRequestDto.getReplay() : false)
                .summary(experienceRequestDto.getSummary())
                .pros(experienceRequestDto.getPros())
                .cons(experienceRequestDto.getCons())
                .notes(experienceRequestDto.getNotes())
                .build();

        experienceRepository.save(experience);
        return experienceMapper.toResponseDto(experience);
    }

    @Override
    public ExperienceResponseDto update(Long id, ExperienceRequestDto experienceRequestDto, Long userId) {
        Experience experience = experienceRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Experience not found"));

        experience.setRunLabel(experienceRequestDto.getRunLabel());
        experience.setYear(experienceRequestDto.getYear());
        experience.setStatus(experienceRequestDto.getStatus());
        experience.setRating(experienceRequestDto.getRating());
        experience.setHours(experienceRequestDto.getHours() != null ? experienceRequestDto.getHours() : 0.0);
        experience.setStartDate(experienceRequestDto.getStartDate());
        experience.setEndDate(experienceRequestDto.getEndDate());
        experience.setPlatform(experienceRequestDto.getPlatform());
        experience.setPlatinum(experienceRequestDto.getPlatinum() != null ? experienceRequestDto.getPlatinum() : false);
        experience.setReplay(experienceRequestDto.getReplay() != null ? experienceRequestDto.getReplay() : false);
        experience.setSummary(experienceRequestDto.getSummary());
        experience.setPros(experienceRequestDto.getPros());
        experience.setCons(experienceRequestDto.getCons());
        experience.setNotes(experienceRequestDto.getNotes());

        experienceRepository.save(experience);
        return experienceMapper.toResponseDto(experience);
    }

    @Override
    public void delete(Long id, Long userId) {
        Experience experience = experienceRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Experience not found"));
        experienceRepository.delete(experience);
    }

    private void requireOwnedGame(Long gameId, Long userId) {
        if (!gameRepository.findByIdAndUserId(gameId, userId).isPresent()) {
            throw new ResourceNotFoundException("Game not found");
        }
    }
}
