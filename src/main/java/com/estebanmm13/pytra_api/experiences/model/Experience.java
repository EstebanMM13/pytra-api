package com.estebanmm13.pytra_api.experiences.model;

import com.estebanmm13.pytra_api.platforms.model.Platform;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "experiences")
public class Experience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long gameId;

    @Column(nullable = false)
    private String runLabel;

    private Integer year;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExperienceStatus status;

    @Column(precision = 4, scale = 2)
    private BigDecimal rating;

    @Column(nullable = false)
    private Double hours;

    private LocalDate startDate;

    private LocalDate endDate;

    /** Catalog entry (platform_id). Eager: tiny global table, always rendered with the run. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "platform_id", nullable = false)
    private Platform platform;

    @Column(nullable = false)
    private Boolean platinum;

    /** "100%": every trophy/achievement of the game obtained. Independent of {@link #platinum}. */
    @Column(nullable = false)
    private Boolean fullCompletion;

    @Column(nullable = false)
    private Boolean replay;

    /** Beaten (COMPLETADO) but still being played: the postgame. Always false on other statuses. */
    @Builder.Default
    @Column(nullable = false)
    private Boolean postgame = false;

    /** When the postgame ended; setting it ends the postgame ({@link #postgame} false). */
    private LocalDate postgameEndDate;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String pros;

    @Column(columnDefinition = "TEXT")
    private String cons;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** When the run was added (rows older than V39 hold an estimate); never changes after the insert. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * True while the user is playing the run: in progress, or completed with its postgame still going. The
     * diary, the platform sync and "playing now" treat these runs alike.
     */
    public boolean isBeingPlayed() {
        return status == ExperienceStatus.EN_CURSO
                || (status == ExperienceStatus.COMPLETADO && Boolean.TRUE.equals(postgame));
    }
}
