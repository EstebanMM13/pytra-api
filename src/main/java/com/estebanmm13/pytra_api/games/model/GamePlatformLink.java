package com.estebanmm13.pytra_api.games.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "game_platform_links", uniqueConstraints = @UniqueConstraint(columnNames = {"game_id", "platform"}))
public class GamePlatformLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExternalPlatform platform;

    @Column(nullable = false)
    private String externalId;

    @Column(nullable = false)
    private Long lastSyncedPlaytimeMinutes;

    @Column(nullable = false)
    private LocalDateTime lastSyncedAt;

    // Experience "canónica" que Steam mantiene al día para juegos SINGLEPLAYER
    // (ONLINE/HYBRID acumulan en OnlinePlaytime en su lugar). Long plano, no
    // @ManyToOne: Experience vive en el módulo experiences, no en games.
    // Null hasta que el juego se confirma como SINGLEPLAYER y se crea esa Experience.
    private Long experienceId;

    // Last time played according to the platform (Steam rtime_last_played). Null when the
    // platform never reported it (never played, or not synced since this column was added).
    private Instant lastPlayedAt;
}
