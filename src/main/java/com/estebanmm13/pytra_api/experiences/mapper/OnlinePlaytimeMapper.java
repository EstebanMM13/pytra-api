package com.estebanmm13.pytra_api.experiences.mapper;

import com.estebanmm13.pytra_api.experiences.dto.onlinePlaytime.OnlinePlaytimeResponseDto;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.model.Ratings;
import org.springframework.stereotype.Component;

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
                onlinePlaytime.getUpdatedAt()
        );
    }
}
