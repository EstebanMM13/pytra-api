package com.estebanmm13.pytra_api.games.service.sagaService;

import com.estebanmm13.pytra_api.games.dto.saga.SagaRequestDto;
import com.estebanmm13.pytra_api.games.dto.saga.SagaResponseDto;

import java.util.List;

public interface SagaService {
    List<SagaResponseDto> findAllByUser(Long userId);
    SagaResponseDto findById(Long id, Long userId);
    SagaResponseDto create(SagaRequestDto sagaRequestDto, Long userId);
    SagaResponseDto update(Long id, SagaRequestDto sagaRequestDto, Long userId);
    void delete(Long id, Long userId);
}
