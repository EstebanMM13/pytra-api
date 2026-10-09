package com.estebanmm13.pytra_api.experiences.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
