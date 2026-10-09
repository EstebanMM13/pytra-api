package com.estebanmm13.pytra_api.games.controller;

import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import com.estebanmm13.pytra_api.games.dto.game.GameRequestDto;
import com.estebanmm13.pytra_api.games.dto.game.GameResponseDto;
import com.estebanmm13.pytra_api.games.service.gameService.GameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping()
    public ResponseEntity<List<GameResponseDto>> findAllByUser() {
        List<GameResponseDto> gameResponseDtos = gameService.findAllByUser(currentUserResolver.getCurrentUserId());
        return ResponseEntity.ok(gameResponseDtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GameResponseDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(gameService.findById(id, currentUserResolver.getCurrentUserId()));
    }

    @PostMapping()
    public ResponseEntity<GameResponseDto> createGame(
            @Valid @RequestBody GameRequestDto gameRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(gameService.create(gameRequestDto, currentUserResolver.getCurrentUserId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GameResponseDto> updateGame(
            @PathVariable Long id,
            @Valid @RequestBody GameRequestDto gameRequestDto) {
        return ResponseEntity.ok(gameService.update(id, gameRequestDto, currentUserResolver.getCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGame(@PathVariable Long id) {
        gameService.delete(id, currentUserResolver.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
