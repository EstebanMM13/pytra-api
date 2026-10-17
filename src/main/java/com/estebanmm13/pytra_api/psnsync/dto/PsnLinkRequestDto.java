package com.estebanmm13.pytra_api.psnsync.dto;

/** Body of PUT /link. Format is validated by the service (400 PSN_INVALID_ONLINE_ID). */
public record PsnLinkRequestDto(String onlineId) {
}
