package com.estebanmm13.pytra_api.experiences.model;

import com.estebanmm13.pytra_api.platforms.model.Platform;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
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

    /** Catalog platforms played online on (optional). Lazy: repository methods that map it fetch it. */
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "online_playtime_platforms",
            joinColumns = @JoinColumn(name = "online_playtime_id"),
            inverseJoinColumns = @JoinColumn(name = "platform_id")
    )
    private Set<Platform> platforms = new HashSet<>();

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
