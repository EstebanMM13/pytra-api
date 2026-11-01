package com.estebanmm13.pytra_api.achievements.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** An achievement the user unlocked once; it stays unlocked whatever happens to the data later. */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "unlocked_achievements")
public class UnlockedAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** The web's achievement id (the catalog lives in the web). */
    @Column(nullable = false, length = 64)
    private String achievementId;

    /** When it was first recorded; recording it again never changes it. */
    @Column(nullable = false)
    private LocalDateTime unlockedAt;
}
