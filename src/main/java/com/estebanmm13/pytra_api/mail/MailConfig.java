package com.estebanmm13.pytra_api.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Slf4j
@Configuration
public class MailConfig {

    @Bean
    public EmailSender emailSender(
            @Value("${app.mail.brevo-api-key:}") String brevoApiKey,
            @Value("${app.mail.from-email:}") String fromEmail,
            @Value("${app.mail.from-name:Pytra}") String fromName,
            Environment environment) {

        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                // An empty BREVO_API_KEY passes placeholder resolution; never silently drop emails in prod.
                throw new IllegalStateException("BREVO_API_KEY must not be blank in the prod profile");
            }
            // No profile (plain local run) or an explicit dev/local profile: show full links in the log.
            boolean localDevelopment = environment.getActiveProfiles().length == 0
                    || environment.acceptsProfiles(Profiles.of("dev", "local"));
            log.warn("app.mail.brevo-api-key is not set: emails will be logged instead of sent");
            return new LoggingEmailSender(localDevelopment);
        }
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("app.mail.from-email is required when app.mail.brevo-api-key is set");
        }
        log.info("Emails will be sent through Brevo from {}", fromEmail);
        return new BrevoEmailSender(brevoApiKey.trim(), fromEmail.trim(), fromName);
    }
}
