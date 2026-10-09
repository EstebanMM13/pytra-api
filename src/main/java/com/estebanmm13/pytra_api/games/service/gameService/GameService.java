package com.estebanmm13.pytra_api.games.service.gameService;

import com.estebanmm13.pytra_api.games.dto.game.GameRequestDto;
import com.estebanmm13.pytra_api.games.dto.game.GameResponseDto;

import java.util.List;

public interface GameService {
    List<GameResponseDto> findAllByUser(Long userId);
    GameResponseDto findById(Long id, Long userId);
    GameResponseDto create(GameRequestDto gameRequestDto, Long userId);
    GameResponseDto update(Long id, GameRequestDto gameRequestDto, Long userId);
    void delete(Long id, Long userId);
}
