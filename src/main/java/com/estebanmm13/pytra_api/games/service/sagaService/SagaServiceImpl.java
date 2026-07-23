package com.estebanmm13.pytra_api.games.service.sagaService;

import com.estebanmm13.pytra_api.error.DuplicateResourceException;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.games.dto.saga.SagaRequestDto;
import com.estebanmm13.pytra_api.games.dto.saga.SagaResponseDto;
import com.estebanmm13.pytra_api.games.mapper.SagaMapper;
import com.estebanmm13.pytra_api.games.model.Saga;
import com.estebanmm13.pytra_api.games.repository.SagaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SagaServiceImpl implements SagaService {

    private final SagaRepository sagaRepository;
    private final SagaMapper sagaMapper;


    @Override
    public List<SagaResponseDto> findAllByUser(Long userId) {
        List<Saga> sagas = sagaRepository.findAllByUserId(userId);
        List<SagaResponseDto> sagaResponseDtos = new ArrayList<>();
        for (Saga saga : sagas) {
            sagaResponseDtos.add(sagaMapper.toResponseDto(saga));
        }
        return sagaResponseDtos;
    }

    @Override
    public SagaResponseDto findById(Long id, Long userId) {

        Optional<Saga> saga = sagaRepository.findByIdAndUserId(id, userId);
        if (saga.isPresent()) {
            return sagaMapper.toResponseDto(saga.get());
        }else {
            throw new ResourceNotFoundException("Saga not found");
        }
    }

    @Override
    public SagaResponseDto create(SagaRequestDto sagaRequestDto, Long userId) {

        if (sagaRepository.existsByUserIdAndNameIgnoreCase(userId, sagaRequestDto.getName())) {
            throw new DuplicateResourceException("Saga already exists");
        }else {
            Saga saga = Saga.builder()
                    .userId(userId)
                    .name(sagaRequestDto.getName())
                    .build();
            sagaRepository.save(saga);
            return sagaMapper.toResponseDto(saga);
        }
    }

    @Override
    public SagaResponseDto update(Long id, SagaRequestDto sagaRequestDto, Long userId) {

        if (sagaRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, sagaRequestDto.getName(),id)) {
            throw new DuplicateResourceException("Saga already exists");
        }
        Optional<Saga> saga = sagaRepository.findByIdAndUserId(id,userId);
        if (saga.isPresent()) {
            saga.get().setName(sagaRequestDto.getName());
            sagaRepository.save(saga.get());
            return sagaMapper.toResponseDto(saga.get());
        }else{
            throw new ResourceNotFoundException("Saga not found");
        }
    }

    @Override
    public void delete(Long id, Long userId) {
        Optional<Saga> saga = sagaRepository.findByIdAndUserId(id,userId);
        if (saga.isPresent()) {
           sagaRepository.delete(saga.get());
        }else{
            throw new ResourceNotFoundException("Saga not found");
        }
    }
}
