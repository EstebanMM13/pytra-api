package com.estebanmm13.pytra_api.diary.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * A game played on a diary day. When {@link #countsHours} is true the hours were added to the game's
 * data: to the run {@link #experienceId}, or to the game's online playtime when it is null.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "diary_entry_games", uniqueConstraints = @UniqueConstraint(columnNames = {"entry_id", "game_id"}))
public class DiaryEntryGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false)
    private DiaryEntry entry;

    @Column(nullable = false)
    private Long gameId;

    private Long experienceId;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal hours;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private boolean countsHours;
}
