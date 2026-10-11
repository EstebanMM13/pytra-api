package com.estebanmm13.pytra_api.platforms.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A gaming platform of the global catalog (shared by every user, like genres).
 *
 * <p>{@code legacyCode} is the value of the old fixed Platform enum (PC, PS5, PS4, XBOX, SWITCH, MOBILE)
 * for the entries that replaced it; null for everything added later.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "platforms")
public class Platform {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(length = 7)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PlatformFamily family;

    @Column(length = 20, unique = true)
    private String legacyCode;

    @Column(nullable = false)
    private Integer sortOrder;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
