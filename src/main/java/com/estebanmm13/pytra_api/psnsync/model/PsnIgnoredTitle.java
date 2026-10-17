package com.estebanmm13.pytra_api.psnsync.model;

import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A PSN title the user does not want in Pytra. Its pending placeholder is deleted on ignore, and the
 * sync skips the title id from then on instead of recreating it.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "psn_ignored_titles", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "title_id"}))
public class PsnIgnoredTitle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String titleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExternalPlatform platform;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDateTime ignoredAt;
}
