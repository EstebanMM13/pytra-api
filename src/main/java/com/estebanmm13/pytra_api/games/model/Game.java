package com.estebanmm13.pytra_api.games.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "games")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    private String developer;

    private String publisher;

    private LocalDate releaseDate;

    @Enumerated(EnumType.STRING)
    private GameCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saga_id")
    private Saga saga;

    private String coverImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus reviewStatus;

    @Builder.Default
    @ManyToMany
    @JoinTable(
            name = "game_genres",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private Set<Genre> genres = new HashSet<>();

    /** Name as a platform sync imported it; null for games created by hand. See V26. */
    private String importedName;

    /** Cover as a platform sync imported it; null for games created by hand. See V26. */
    private String importedCoverUrl;

    /** When IGDB was last asked about this pending game; null = never (the next sync queues it). */
    private LocalDateTime igdbLookupAt;

    /**
     * Call BEFORE applying a user edit: the imported name/cover is forgotten as soon as the user changes
     * that value, so the IGDB enrichment never replaces something the user ever touched, even if it is
     * later set back to the original.
     */
    public void forgetEditedImportedValues(String newName, String newCoverImageUrl) {
        if (!Objects.equals(newName, name)) importedName = null;
        if (!Objects.equals(newCoverImageUrl, coverImageUrl)) importedCoverUrl = null;
    }

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
