package com.estebanmm13.pytra_api.experiences.mapper;

import com.estebanmm13.pytra_api.experiences.dto.onlinePlaytime.OnlinePlaytimeResponseDto;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.model.Ratings;
import com.estebanmm13.pytra_api.platforms.mapper.PlatformMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OnlinePlaytimeMapper {

    public OnlinePlaytimeResponseDto toResponseDto(OnlinePlaytime onlinePlaytime) {
        if (onlinePlaytime == null) return null;
        return new OnlinePlaytimeResponseDto(
                onlinePlaytime.getId(),
                onlinePlaytime.getGameId(),
                onlinePlaytime.getTotalHours(),
                onlinePlaytime.getLastSessionAt(),
                Ratings.normalize(onlinePlaytime.getGeneralRating()),
                onlinePlaytime.getNotes(),
                Boolean.TRUE.equals(onlinePlaytime.getPlatinum()),
                Boolean.TRUE.equals(onlinePlaytime.getFullCompletion()),
                onlinePlaytime.getUpdatedAt(),
                onlinePlaytime.getPlatforms().stream()
                        .sorted(PlatformMapper.CATALOG_ORDER)
                        .map(PlatformMapper::toRefDto)
                        .toList(),
                Map.copyOf(onlinePlaytime.getPlatformHours())
        );
    }
}
