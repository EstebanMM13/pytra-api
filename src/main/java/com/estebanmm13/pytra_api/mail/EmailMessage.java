package com.estebanmm13.pytra_api.mail;

/**
 * A single transactional email. Both bodies are sent so clients without HTML support still get the content.
 */
public record EmailMessage(String to, String subject, String htmlContent, String textContent) {
}
