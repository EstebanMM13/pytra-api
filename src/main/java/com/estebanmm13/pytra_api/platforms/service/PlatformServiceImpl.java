package com.estebanmm13.pytra_api.platforms.service;

import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.platforms.dto.PlatformRequestDto;
import com.estebanmm13.pytra_api.platforms.dto.PlatformResponseDto;
import com.estebanmm13.pytra_api.platforms.mapper.PlatformMapper;
import com.estebanmm13.pytra_api.platforms.model.Platform;
import com.estebanmm13.pytra_api.platforms.model.PlatformFamily;
import com.estebanmm13.pytra_api.platforms.repository.PlatformRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PlatformServiceImpl implements PlatformService {

    /** User-created entries go after the seeded ones. */
    static final int USER_SORT_ORDER = 1000;

    private final PlatformRepository platformRepository;
    private final PlatformCreationGuard creationGuard;

    @Override
    public List<PlatformResponseDto> findAll() {
        return platformRepository.findAllByOrderBySortOrderAscNameAscIdAsc().stream()
                .map(PlatformMapper::toResponseDto)
                .toList();
    }

    @Override
    public PlatformCreationResult createIfMissing(Long userId, PlatformRequestDto request) {
        String name = request.getName().trim();
        Optional<Platform> existing = platformRepository.findByNameIgnoreCase(name);
        if (existing.isPresent()) {
            return new PlatformCreationResult(PlatformMapper.toResponseDto(existing.get()), false);
        }
        // Capacity first, so a full catalog does not also burn the user's rate-limit slots.
        creationGuard.checkCapacity(platformRepository.count());
        creationGuard.acquire(userId);
        PlatformFamily family = request.getFamily() != null ? request.getFamily() : PlatformFamily.OTHER;
        String color = request.getColor() != null
                ? request.getColor().toUpperCase(Locale.ROOT)
                : family.defaultColor();
        Platform platform = Platform.builder()
                .name(name)
                .family(family)
                .color(color)
                .sortOrder(USER_SORT_ORDER)
                .build();
        try {
            platformRepository.saveAndFlush(platform);
        } catch (DataIntegrityViolationException e) {
            // Another request created the same name in between (unique LOWER(name) index): return that one.
            return platformRepository.findByNameIgnoreCase(name)
                    .map(p -> new PlatformCreationResult(PlatformMapper.toResponseDto(p), false))
                    .orElseThrow(() -> e);
        }
        return new PlatformCreationResult(PlatformMapper.toResponseDto(platform), true);
    }

    @Override
    public Platform resolve(Long platformId, String legacyCode, Platform current) {
        if (platformId != null) {
            return platformRepository.findById(platformId)
                    .orElseThrow(() -> new InvalidRequestException(InvalidRequestException.INVALID_PLATFORM));
        }
        if (legacyCode != null && !legacyCode.isBlank()) {
            String code = legacyCode.trim();
            if (current != null && code.equalsIgnoreCase(PlatformMapper.legacyCodeOf(current))) {
                return current;
            }
            Optional<Platform> byCode = platformRepository.findByLegacyCodeIgnoreCase(code);
            if (byCode.isPresent()) {
                return byCode.get();
            }
        }
        throw new InvalidRequestException(InvalidRequestException.INVALID_PLATFORM);
    }

    @Override
    public Platform requireByLegacyCode(String legacyCode) {
        return platformRepository.findByLegacyCodeIgnoreCase(legacyCode)
                .orElseThrow(() -> new IllegalStateException("Seeded platform missing: " + legacyCode));
    }
}
