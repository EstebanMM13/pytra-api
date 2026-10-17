package com.estebanmm13.pytra_api.psnsync.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * The PSN account a Pytra user typed on the Sync page. No PSN login is involved: the public game
 * list of {@code accountId} is read with Pytra's service account.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "psn_links")
public class PsnLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    // Not unique across users on purpose (ownership is never proven, see V25).
    @Column(nullable = false)
    private String accountId;

    // As PSN spells it; refreshed when the user links again after renaming the account.
    @Column(nullable = false)
    private String onlineId;

    @Column(nullable = false)
    private LocalDateTime linkedAt;

    // Last successful library sync (manual or scheduled); null until the first one.
    private LocalDateTime lastSyncAt;
}
