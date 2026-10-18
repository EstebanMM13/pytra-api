package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.metadata.dto.IgdbGameCandidateDto;
import com.estebanmm13.pytra_api.metadata.dto.IgdbGameMetadataDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IgdbPendingMatchTest {

    private static final Set<Integer> PS4 = Set.of(IgdbPendingMatch.IGDB_PS4);

    @Test
    void steamMapsToPcAndEachPsnConsoleToItsOwnPlatform() {
        assertThat(IgdbPendingMatch.igdbPlatformIds(List.of(ExternalPlatform.STEAM))).containsExactly(6);
        assertThat(IgdbPendingMatch.igdbPlatformIds(List.of(ExternalPlatform.PSN_PS4))).containsExactly(48);
        assertThat(IgdbPendingMatch.igdbPlatformIds(List.of(ExternalPlatform.PSN_PS5))).containsExactly(167);
        assertThat(IgdbPendingMatch.igdbPlatformIds(List.of(ExternalPlatform.PSN_PS5, ExternalPlatform.STEAM)))
                .containsExactly(6, 167);
    }

    @Test
    void psnWithUnknownConsoleUsesBothConsoles() {
        assertThat(IgdbPendingMatch.igdbPlatformIds(List.of(ExternalPlatform.PSN))).containsExactly(48, 167);
    }

    @Test
    void unsyncedPlatformsMapToNothing() {
        assertThat(IgdbPendingMatch.igdbPlatformIds(List.of(ExternalPlatform.MANUAL, ExternalPlatform.XBOX))).isEmpty();
    }

    @Test
    void sameNormalizedNameOnTheSamePlatformIsConfident() {
        IgdbGameCandidateDto spiderMan = candidate(1074, "Marvel's Spider-Man", 48, 6);

        assertThat(IgdbPendingMatch.select("Marvel’s Spider-Man™", PS4, List.of(
                candidate(19565, "Marvel's Spider-Man: Miles Morales", 48), spiderMan)))
                .contains(spiderMan);
    }

    @Test
    void consoleSuffixOfThePendingNameDoesNotPreventAMatch() {
        IgdbGameCandidateDto gta = candidate(1020, "Grand Theft Auto V", 167);

        assertThat(IgdbPendingMatch.select("Grand Theft Auto V (PlayStation®5)", Set.of(167), List.of(gta)))
                .contains(gta);
    }

    @Test
    void romanNumeralInTheIgdbNameMatchesDigitsInThePendingName() {
        IgdbGameCandidateDto hades2 = candidate(1, "Hades II", 6);

        assertThat(IgdbPendingMatch.select("Hades 2", Set.of(6), List.of(candidate(2, "Hades", 6), hades2)))
                .contains(hades2);
    }

    @Test
    void sameNameOnAnotherPlatformIsNotAMatch() {
        assertThat(IgdbPendingMatch.select("Halo Infinite", PS4, List.of(candidate(1, "Halo Infinite", 6, 169))))
                .isEmpty();
    }

    @Test
    void nearNameIsNotAMatch() {
        assertThat(IgdbPendingMatch.select("Hades", Set.of(6), List.of(candidate(1, "Hades II", 6)))).isEmpty();
    }

    @Test
    void twoGamesWithTheSameNameOnThePlatformAreAmbiguous() {
        assertThat(IgdbPendingMatch.select("Doom", Set.of(6), List.of(
                candidate(1, "DOOM", 6), candidate(2, "Doom", 6)))).isEmpty();
    }

    @Test
    void theSameGameListedTwiceIsStillConfident() {
        IgdbGameCandidateDto doom = candidate(1, "DOOM", 6);

        assertThat(IgdbPendingMatch.select("Doom", Set.of(6), List.of(doom, candidate(1, "DOOM", 6)))).contains(doom);
    }

    @Test
    void noPlatformOrNoNameIsNeverConfident() {
        assertThat(IgdbPendingMatch.select("Doom", Set.of(), List.of(candidate(1, "Doom", 6)))).isEmpty();
        assertThat(IgdbPendingMatch.select("™", Set.of(6), List.of(candidate(1, "Doom", 6)))).isEmpty();
        assertThat(IgdbPendingMatch.select(null, Set.of(6), List.of(candidate(1, "Doom", 6)))).isEmpty();
    }

    @Test
    void categoryComesFromTheGameModes() {
        assertThat(IgdbPendingMatch.suggestCategory(Set.of(1))).isEqualTo(GameCategory.SINGLEPLAYER);
        assertThat(IgdbPendingMatch.suggestCategory(Set.of(2))).isEqualTo(GameCategory.ONLINE);
        assertThat(IgdbPendingMatch.suggestCategory(Set.of(5, 6))).isEqualTo(GameCategory.ONLINE);
        assertThat(IgdbPendingMatch.suggestCategory(Set.of(1, 3))).isEqualTo(GameCategory.HYBRID);
        assertThat(IgdbPendingMatch.suggestCategory(Set.of(1, 2))).isEqualTo(GameCategory.HYBRID);
        assertThat(IgdbPendingMatch.suggestCategory(Set.of())).isNull();
    }

    private static IgdbGameCandidateDto candidate(long id, String name, Integer... platformIds) {
        IgdbGameMetadataDto metadata = new IgdbGameMetadataDto(id, name, null, null, null, List.of(), null, List.of());
        return new IgdbGameCandidateDto(metadata, Set.of(platformIds), Set.of(1));
    }
}
