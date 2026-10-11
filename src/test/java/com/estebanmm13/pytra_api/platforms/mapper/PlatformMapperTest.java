package com.estebanmm13.pytra_api.platforms.mapper;

import com.estebanmm13.pytra_api.platforms.TestPlatforms;
import com.estebanmm13.pytra_api.platforms.model.Platform;
import com.estebanmm13.pytra_api.platforms.model.PlatformFamily;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformMapperTest {

    @Test
    void legacyCodeIsTheSeedCodeOrOther() {
        assertThat(PlatformMapper.legacyCodeOf(TestPlatforms.XBOX)).isEqualTo("XBOX");
        assertThat(PlatformMapper.legacyCodeOf(TestPlatforms.DS)).isEqualTo(PlatformMapper.LEGACY_OTHER);
        assertThat(PlatformMapper.legacyCodeOf(null)).isNull();
    }

    @Test
    void colorFallsBackToTheFamilyDefault() {
        Platform noColor = Platform.builder().id(5L).name("Neo Geo").family(PlatformFamily.OTHER).sortOrder(1000).build();
        Platform nothing = Platform.builder().id(6L).name("Unknown").sortOrder(1000).build();

        assertThat(PlatformMapper.colorOf(noColor)).isEqualTo(PlatformFamily.OTHER.defaultColor());
        assertThat(PlatformMapper.colorOf(nothing)).isNull();
        assertThat(PlatformMapper.toRefDto(noColor).color()).isEqualTo(PlatformFamily.OTHER.defaultColor());
    }
}
