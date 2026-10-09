package com.estebanmm13.pytra_api.games.mapper;

import com.estebanmm13.pytra_api.games.dto.saga.SagaResponseDto;
import com.estebanmm13.pytra_api.games.model.Saga;
import org.springframework.stereotype.Component;

@Component
public class SagaMapper {

    public SagaResponseDto toResponseDto(Saga saga) {
        if(saga == null) return null;
        return new SagaResponseDto(
                saga.getId(),
                saga.getName(),
                saga.getUpdatedAt()
        );
    }
}
