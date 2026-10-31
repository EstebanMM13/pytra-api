package com.estebanmm13.pytra_api.experiences.model;

import com.estebanmm13.pytra_api.platforms.model.Platform;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "online_playtimes", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "game_id"}))
public class OnlinePlaytime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long gameId;

    @Column(nullable = false)
    private Double totalHours;

    private LocalDateTime lastSessionAt;

    @Column(precision = 4, scale = 2)
    private BigDecimal generalRating;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Set by the user only; the platform syncs never touch it. */
    @Builder.Default
    @Column(nullable = false)
    private Boolean platinum = false;

    /** "100%": every trophy/achievement of the game obtained. Independent of {@link #platinum}; user-set only. */
    @Builder.Default
    @Column(nullable = false)
    private Boolean fullCompletion = false;

    /** Catalog platforms played online on (optional). Lazy: repository methods that map it fetch it. */
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "online_playtime_platforms",
            joinColumns = @JoinColumn(name = "online_playtime_id"),
            inverseJoinColumns = @JoinColumn(name = "platform_id")
    )
    private Set<Platform> platforms = new HashSet<>();

    /**
     * Online hours assigned to each of {@link #platforms}, by platform id: a breakdown of {@link #totalHours}
     * (never above it when saved by the user). Only kept with two or more platforms; empty means no breakdown.
     */
    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "online_playtime_platform_hours", joinColumns = @JoinColumn(name = "online_playtime_id"))
    @MapKeyColumn(name = "platform_id")
    @Column(name = "hours", nullable = false)
    private Map<Long, Double> platformHours = new HashMap<>();

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
