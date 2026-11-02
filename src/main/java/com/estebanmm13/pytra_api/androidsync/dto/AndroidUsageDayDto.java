package com.estebanmm13.pytra_api.androidsync.dto;

import java.time.LocalDate;

/**
 * Foreground minutes of one package on one local day, as measured by the phone: the day's whole total, sent
 * again on every sync. Checked per item by the service (missing fields, date window, 0-1440 minutes): a bad
 * item is ignored and never fails the rest of the sync.
 */
public record AndroidUsageDayDto(LocalDate date, String packageName, Integer minutes) {
}
