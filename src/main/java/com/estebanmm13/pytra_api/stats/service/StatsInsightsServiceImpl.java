package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.diary.repository.DiaryDayTotals;
import com.estebanmm13.pytra_api.diary.repository.DiaryEntryGameRepository;
import com.estebanmm13.pytra_api.stats.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

@Service
public class StatsInsightsServiceImpl implements StatsInsightsService {

    private final DiaryEntryGameRepository diaryEntryGameRepository;
    private final Clock clock;

    @Autowired
    public StatsInsightsServiceImpl(DiaryEntryGameRepository diaryEntryGameRepository) {
        this(diaryEntryGameRepository, Clock.systemDefaultZone());
    }

    StatsInsightsServiceImpl(DiaryEntryGameRepository diaryEntryGameRepository, Clock clock) {
        this.diaryEntryGameRepository = diaryEntryGameRepository;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- activity

    @Override
    public ActivityStatsDto getActivity(Long userId, Integer year) {
        LocalDate today = LocalDate.now(clock);
        int effectiveYear = year != null ? year : today.getYear();
        StatsServiceImpl.requireValidYear(effectiveYear);

        List<DiaryDayTotals> rows = diaryEntryGameRepository.sumHoursByDay(
                userId, LocalDate.of(effectiveYear, 1, 1), LocalDate.of(effectiveYear, 12, 31));

        BigDecimal totalHours = BigDecimal.ZERO;
        long[] daysByWeekday = new long[7];
        BigDecimal[] hoursByWeekday = new BigDecimal[7];
        Arrays.fill(hoursByWeekday, BigDecimal.ZERO);
        List<ActivityStatsDto.Day> days = new ArrayList<>(rows.size());
        for (DiaryDayTotals row : rows) {
            BigDecimal hours = row.hours() != null ? row.hours() : BigDecimal.ZERO;
            totalHours = totalHours.add(hours);
            int weekday = row.date().getDayOfWeek().getValue() - 1;
            daysByWeekday[weekday]++;
            hoursByWeekday[weekday] = hoursByWeekday[weekday].add(hours);
            days.add(new ActivityStatsDto.Day(row.date(), hours.doubleValue(),
                    row.gameCount() != null ? row.gameCount() : 0L));
        }

        List<ActivityStatsDto.Weekday> weekdays = new ArrayList<>(7);
        for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
            int index = dayOfWeek.getValue() - 1;
            weekdays.add(new ActivityStatsDto.Weekday(dayOfWeek.getValue(), dayOfWeek.name(),
                    daysByWeekday[index], hoursByWeekday[index].doubleValue()));
        }

        List<LocalDate> playedDates = diaryEntryGameRepository.findPlayedDates(userId);
        return new ActivityStatsDto(
                effectiveYear,
                totalHours.doubleValue(),
                days.size(),
                days,
                ActivityStreaks.current(playedDates, today),
                ActivityStreaks.longest(playedDates),
                weekdays);
    }
}
