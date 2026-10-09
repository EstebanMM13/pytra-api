package com.estebanmm13.pytra_api.mail;

/**
 * Outbound email port. Implementations may throw on delivery failure; callers decide how to degrade.
 */
public interface EmailSender {

    void send(EmailMessage message);
}
