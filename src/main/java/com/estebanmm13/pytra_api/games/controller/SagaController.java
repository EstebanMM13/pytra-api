package com.estebanmm13.pytra_api.games.controller;

import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import com.estebanmm13.pytra_api.games.dto.saga.SagaRequestDto;
import com.estebanmm13.pytra_api.games.dto.saga.SagaResponseDto;
import com.estebanmm13.pytra_api.games.service.sagaService.SagaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/sagas")
@RequiredArgsConstructor
public class SagaController {

    private final SagaService sagaService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping()
    public ResponseEntity<List<SagaResponseDto>> findAllByUser() {
        List<SagaResponseDto> sagaResponseDtos = sagaService.findAllByUser(currentUserResolver.getCurrentUserId());
        return ResponseEntity.ok(sagaResponseDtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SagaResponseDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(sagaService.findById(id, currentUserResolver.getCurrentUserId()));
    }

    @PostMapping()
    public ResponseEntity<SagaResponseDto> createSaga(
            @Valid @RequestBody SagaRequestDto sagaRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sagaService.create(sagaRequestDto, currentUserResolver.getCurrentUserId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SagaResponseDto> updateSaga(
            @PathVariable Long id,
            @Valid @RequestBody SagaRequestDto sagaRequestDto) {
        return ResponseEntity.ok(sagaService.update(id, sagaRequestDto, currentUserResolver.getCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<SagaResponseDto> deleteSaga(
            @PathVariable Long id
    ) {
        sagaService.delete(id, currentUserResolver.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
