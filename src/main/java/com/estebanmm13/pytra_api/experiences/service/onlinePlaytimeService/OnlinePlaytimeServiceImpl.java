package com.estebanmm13.pytra_api.experiences.service.onlinePlaytimeService;

import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.experiences.dto.onlinePlaytime.OnlinePlaytimeRequestDto;
import com.estebanmm13.pytra_api.experiences.dto.onlinePlaytime.OnlinePlaytimeResponseDto;
import com.estebanmm13.pytra_api.experiences.mapper.OnlinePlaytimeMapper;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OnlinePlaytimeServiceImpl implements OnlinePlaytimeService {

    private final OnlinePlaytimeRepository onlinePlaytimeRepository;
    private final GameRepository gameRepository;
    private final OnlinePlaytimeMapper onlinePlaytimeMapper;

    @Override
    public OnlinePlaytimeResponseDto findByGame(Long gameId, Long userId) {
        requireOwnedGame(gameId, userId);

        OnlinePlaytime onlinePlaytime = onlinePlaytimeRepository.findByGameIdAndUserId(gameId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Online playtime not found"));
        return onlinePlaytimeMapper.toResponseDto(onlinePlaytime);
    }

    @Override
    public OnlinePlaytimeResponseDto upsert(Long gameId, OnlinePlaytimeRequestDto onlinePlaytimeRequestDto, Long userId) {
        requireOwnedGame(gameId, userId);

        Optional<OnlinePlaytime> existing = onlinePlaytimeRepository.findByGameIdAndUserId(gameId, userId);

        OnlinePlaytime onlinePlaytime = existing.orElseGet(() ->
                OnlinePlaytime.builder()
                        .userId(userId)
                        .gameId(gameId)
                        .build()
        );

        onlinePlaytime.setTotalHours(onlinePlaytimeRequestDto.getTotalHours());
        onlinePlaytime.setLastSessionAt(onlinePlaytimeRequestDto.getLastSessionAt());
        onlinePlaytime.setGeneralRating(onlinePlaytimeRequestDto.getGeneralRating());
        onlinePlaytime.setNotes(onlinePlaytimeRequestDto.getNotes());

        onlinePlaytimeRepository.save(onlinePlaytime);
        return onlinePlaytimeMapper.toResponseDto(onlinePlaytime);
    }

    private void requireOwnedGame(Long gameId, Long userId) {
        if (!gameRepository.findByIdAndUserId(gameId, userId).isPresent()) {
            throw new ResourceNotFoundException("Game not found");
        }
    }
}
