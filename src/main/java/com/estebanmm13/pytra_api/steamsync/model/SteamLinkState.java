package com.estebanmm13.pytra_api.steamsync.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Correlaciona el GET /login (autenticado, sabe el userId) con el GET /callback
 * (público, lo llama Steam sin cabecera Authorization) a través de la redirección
 * externa a steamcommunity.com. Solo el token aparece en la URL; el userId nunca
 * viaja en claro en el redirect, así que no se puede falsificar sin pasar antes
 * por /login con un JWT válido propio.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "steam_link_states")
public class SteamLinkState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime consumedAt;
}
