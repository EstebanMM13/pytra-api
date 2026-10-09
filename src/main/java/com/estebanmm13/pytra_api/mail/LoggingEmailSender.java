package com.estebanmm13.pytra_api.mail;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * Fallback used when no Brevo API key is configured: nothing is sent, the email is logged instead.
 * Links (and the recipient) are only logged in full for local development, so verification and reset flows
 * can be completed by hand; otherwise tokens are masked so logs never hold usable credentials.
 */
@Slf4j
public class LoggingEmailSender implements EmailSender {

    private static final Pattern TOKEN_PARAM = Pattern.compile("token=[A-Za-z0-9_-]+");

    private final boolean revealLinks;

    public LoggingEmailSender(boolean revealLinks) {
        this.revealLinks = revealLinks;
    }

    @Override
    public void send(EmailMessage message) {
        if (revealLinks) {
            log.info("[mail disabled] To: {} | Subject: {}\n{}", message.to(), message.subject(), message.textContent());
        } else {
            String masked = TOKEN_PARAM.matcher(message.textContent()).replaceAll("token=***");
            log.info("[mail disabled] Subject: {}\n{}", message.subject(), masked);
        }
    }
}
