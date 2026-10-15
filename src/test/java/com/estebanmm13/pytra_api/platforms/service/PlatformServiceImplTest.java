package com.estebanmm13.pytra_api.platforms.service;

import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.error.ResourceLimitExceededException;
import com.estebanmm13.pytra_api.platforms.TestPlatforms;
import com.estebanmm13.pytra_api.platforms.dto.PlatformRequestDto;
import com.estebanmm13.pytra_api.platforms.model.Platform;
import com.estebanmm13.pytra_api.platforms.model.PlatformFamily;
import com.estebanmm13.pytra_api.platforms.repository.PlatformRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlatformServiceImplTest {

    private static final Long USER_ID = 1L;

    private final PlatformRepository repository = mock(PlatformRepository.class);
    private final PlatformCreationGuard guard = mock(PlatformCreationGuard.class);
    private final PlatformServiceImpl service = new PlatformServiceImpl(repository, guard);

    @Test
    void existingNameIsReturnedWithoutCreating() {
        when(repository.findByNameIgnoreCase("ds")).thenReturn(Optional.of(TestPlatforms.DS));

        PlatformCreationResult result = service.createIfMissing(USER_ID, request("  ds ", null, null));

        assertThat(result.created()).isFalse();
        assertThat(result.platform().name()).isEqualTo("DS");
        verify(repository, never()).saveAndFlush(any());
        // Dedupe hits never count toward the cap or the rate limit.
        verifyNoInteractions(guard);
    }

    @Test
    void newEntryIsCheckedAgainstTheCapAndTheUserRateLimit() {
        when(repository.findByNameIgnoreCase("Saturn")).thenReturn(Optional.empty());
        when(repository.count()).thenReturn(42L);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createIfMissing(USER_ID, request("Saturn", null, null));

        verify(guard).checkCapacity(42L);
        verify(guard).acquire(USER_ID);
    }

    @Test
    void fullCatalogRejectsBeforeConsumingTheRateLimit() {
        when(repository.findByNameIgnoreCase("Saturn")).thenReturn(Optional.empty());
        when(repository.count()).thenReturn(200L);
        doThrow(new ResourceLimitExceededException(ResourceLimitExceededException.PLATFORM_CATALOG_FULL))
                .when(guard).checkCapacity(200L);

        assertThatThrownBy(() -> service.createIfMissing(USER_ID, request("Saturn", null, null)))
                .isInstanceOf(ResourceLimitExceededException.class)
                .hasMessage(ResourceLimitExceededException.PLATFORM_CATALOG_FULL);
        verify(guard, never()).acquire(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void newEntryDefaultsToOtherFamilyColorAndUserSortOrder() {
        when(repository.findByNameIgnoreCase("Anbernic RG35XX")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PlatformCreationResult result = service.createIfMissing(USER_ID, request("Anbernic   RG35XX", null, null));

        assertThat(result.created()).isTrue();
        assertThat(result.platform().name()).isEqualTo("Anbernic RG35XX");
        assertThat(result.platform().family()).isEqualTo(PlatformFamily.OTHER);
        assertThat(result.platform().color()).isEqualTo(PlatformFamily.OTHER.defaultColor());
        assertThat(result.platform().sortOrder()).isEqualTo(PlatformServiceImpl.USER_SORT_ORDER);
        assertThat(result.platform().legacyCode()).isNull();
    }

    @Test
    void explicitColorIsUpperCased() {
        when(repository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PlatformCreationResult result = service.createIfMissing(USER_ID, request("Saturn", PlatformFamily.OTHER, "#aabbcc"));

        assertThat(result.platform().color()).isEqualTo("#AABBCC");
    }

    @Test
    void concurrentCreateReturnsTheWinner() {
        Platform winner = Platform.builder().id(99L).name("Saturn").sortOrder(1000).build();
        when(repository.findByNameIgnoreCase("Saturn")).thenReturn(Optional.empty(), Optional.of(winner));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        PlatformCreationResult result = service.createIfMissing(USER_ID, request("Saturn", null, null));

        assertThat(result.created()).isFalse();
        assertThat(result.platform().id()).isEqualTo(99L);
    }

    @Test
    void platformIdWinsOverLegacyCode() {
        when(repository.findById(20L)).thenReturn(Optional.of(TestPlatforms.DS));

        assertThat(service.resolve(20L, "PC", null)).isSameAs(TestPlatforms.DS);
    }

    @Test
    void unknownPlatformIdIsInvalid() {
        when(repository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(7L, null, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage(InvalidRequestException.INVALID_PLATFORM);
    }

    @Test
    void legacyCodeMapsThroughTheCatalog() {
        when(repository.findByLegacyCodeIgnoreCase("switch")).thenReturn(Optional.of(TestPlatforms.SWITCH));

        assertThat(service.resolve(null, " switch ", null)).isSameAs(TestPlatforms.SWITCH);
    }

    @Test
    void echoedLegacyProjectionKeepsTheCurrentPlatform() {
        assertThat(service.resolve(null, "OTHER", TestPlatforms.DS)).isSameAs(TestPlatforms.DS);
        assertThat(service.resolve(null, "XBOX", TestPlatforms.XBOX)).isSameAs(TestPlatforms.XBOX);
        verify(repository, never()).findByLegacyCodeIgnoreCase(any());
    }

    @Test
    void otherWithoutACurrentPlatformIsInvalid() {
        when(repository.findByLegacyCodeIgnoreCase("OTHER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(null, "OTHER", null))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.resolve(null, null, TestPlatforms.PC))
                .isInstanceOf(InvalidRequestException.class);
    }

    private static PlatformRequestDto request(String name, PlatformFamily family, String color) {
        PlatformRequestDto dto = new PlatformRequestDto();
        dto.setName(name);
        dto.setFamily(family);
        dto.setColor(color);
        return dto;
    }
}
