package com.estebanmm13.pytra_api.androidsync.dto;

/**
 * Foreground minutes of one package on one local day, as measured by the phone: the day's whole total, sent
 * again on every sync. Checked per item by the service (missing fields, a date that is not yyyy-MM-dd, date
 * window, 0-1440 minutes): a bad item is ignored and never fails the rest of the sync, which is why the date
 * arrives as text.
 */
public record AndroidUsageDayDto(String date, String packageName, Integer minutes) {
}
