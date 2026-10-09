package com.estebanmm13.pytra_api.steamsync.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A Steam app the user does not want in Pytra. Its pending placeholder game is deleted on
 * ignore, and the sync skips the appid from then on instead of recreating it.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "steam_ignored_apps", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "app_id"}))
public class SteamIgnoredApp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String appId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDateTime ignoredAt;
}
