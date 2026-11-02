package com.estebanmm13.pytra_api.androidsync.dto;

/**
 * Outcome of one sync: {@code recorded} items added new play; {@code ignored} ones did not (unlinked package,
 * date or minutes out of range, no new minute since the last sync, or a day before the link).
 */
public record AndroidUsageSyncResponseDto(int recorded, int ignored) {
}
