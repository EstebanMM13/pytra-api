package com.estebanmm13.pytra_api.steamsync.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "steam_links")
public class SteamLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    // One Steam account per Pytra user (uq_steam_links_steam_id, V13).
    @Column(nullable = false, unique = true)
    private String steamId;

    private String personaName;

    @Column(nullable = false)
    private LocalDateTime linkedAt;

    // Last successful library sync (manual or scheduled); null until the first one.
    private LocalDateTime lastSyncAt;
}
