package com.estebanmm13.pytra_api.psnsync.client;

/**
 * A PSN account resolved from an online ID.
 *
 * @param accountId stable numeric id (survives online ID changes); used by every other call
 * @param onlineId  current online ID as PSN spells it
 */
public record PsnAccount(String accountId, String onlineId) {
}
