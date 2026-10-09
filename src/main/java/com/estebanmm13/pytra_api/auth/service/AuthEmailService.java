package com.estebanmm13.pytra_api.auth.service;

import com.estebanmm13.pytra_api.config.UrlNormalizer;
import com.estebanmm13.pytra_api.mail.EmailMessage;
import com.estebanmm13.pytra_api.mail.EmailSender;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Builds and sends the account emails (Spanish copy). Links point to the frontend pages, which call the API.
 * Emails are handed to a small bounded pool after the transaction commits, so request threads never wait on
 * the provider and response times do not reveal whether an email was sent (account enumeration).
 * Delivery failures are logged and swallowed: the user can always ask for a new email.
 */
@Service
@Slf4j
public class AuthEmailService {

    private static final int POOL_CORE_SIZE = 2;
    private static final int POOL_MAX_SIZE = 4;
    private static final int POOL_QUEUE_CAPACITY = 100;
    // Control characters (CR/LF included) and unicode line/paragraph separators.
    private static final Pattern UNSAFE_TEXT = Pattern.compile("[\\p{Cc}\\p{Zl}\\p{Zp}]");

    private final EmailSender emailSender;
    private final String frontendUrl;
    private final ThreadPoolExecutor executor;

    public AuthEmailService(EmailSender emailSender, @Value("${app.frontend-url}") String frontendUrl) {
        this.emailSender = emailSender;
        this.frontendUrl = UrlNormalizer.normalizeBaseUrl(frontendUrl);
        this.executor = newMailExecutor();
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }

    public void sendVerificationEmail(String to, String usernameDisplay, String rawToken, Duration validFor) {
        String name = sanitize(usernameDisplay);
        String link = frontendUrl + "/verify-email?token=" + rawToken;
        String expiry = describe(validFor);
        String subject = "Confirma tu email en Pytra";

        String text = "Hola, " + name + ":\n\n"
                + "Gracias por registrarte en Pytra. Para activar tu cuenta, confirma tu email abriendo este enlace:\n\n"
                + link + "\n\n"
                + "El enlace caduca en " + expiry + ". Si caduca, puedes solicitar uno nuevo desde la pantalla de inicio de sesión.\n\n"
                + "Si no has creado esta cuenta, ignora este mensaje.";

        String html = layout(
                "Hola, " + HtmlUtils.htmlEscape(name) + ":",
                "Gracias por registrarte en Pytra. Para activar tu cuenta, confirma tu email.",
                "Confirmar email",
                link,
                "El enlace caduca en " + expiry + ". Si caduca, puedes solicitar uno nuevo desde la pantalla de inicio de sesión.",
                "Si no has creado esta cuenta, ignora este mensaje.");

        sendAfterCommit(new EmailMessage(to, subject, html, text));
    }

    public void sendPasswordResetEmail(String to, String usernameDisplay, String rawToken, Duration validFor) {
        String name = sanitize(usernameDisplay);
        String link = frontendUrl + "/reset-password?token=" + rawToken;
        String expiry = describe(validFor);
        String subject = "Restablece tu contraseña de Pytra";

        String text = "Hola, " + name + ":\n\n"
                + "Hemos recibido una solicitud para restablecer tu contraseña. Puedes elegir una nueva desde este enlace:\n\n"
                + link + "\n\n"
                + "El enlace caduca en " + expiry + " y solo puede usarse una vez.\n\n"
                + "Si no has solicitado este cambio, ignora este mensaje: tu contraseña no cambiará.";

        String html = layout(
                "Hola, " + HtmlUtils.htmlEscape(name) + ":",
                "Hemos recibido una solicitud para restablecer tu contraseña.",
                "Elegir nueva contraseña",
                link,
                "El enlace caduca en " + expiry + " y solo puede usarse una vez.",
                "Si no has solicitado este cambio, ignora este mensaje: tu contraseña no cambiará.");

        sendAfterCommit(new EmailMessage(to, subject, html, text));
    }

    /**
     * Queues the email once the surrounding transaction commits, so it never references a token that was
     * rolled back. Without a transaction it is queued immediately.
     */
    private void sendAfterCommit(EmailMessage message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendAsync(message);
                }
            });
        } else {
            sendAsync(message);
        }
    }

    private void sendAsync(EmailMessage message) {
        try {
            executor.execute(() -> sendSafely(message));
        } catch (RejectedExecutionException e) {
            // Pool and queue are full (or shutting down): drop it; the user can request the email again.
            log.error("Email '{}' dropped: mail queue is full", message.subject());
        }
    }

    // Logs never include the recipient, the provider's response body or the exception message
    // (which may echo request details); the status code is enough to diagnose provider issues.
    private void sendSafely(EmailMessage message) {
        try {
            emailSender.send(message);
        } catch (RestClientResponseException e) {
            log.error("Could not send email '{}': provider answered HTTP {}", message.subject(), e.getStatusCode().value());
        } catch (Exception e) {
            log.error("Could not send email '{}': {}", message.subject(), e.getClass().getSimpleName());
        }
    }

    private static ThreadPoolExecutor newMailExecutor() {
        AtomicInteger threadCount = new AtomicInteger();
        return new ThreadPoolExecutor(
                POOL_CORE_SIZE, POOL_MAX_SIZE, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(POOL_QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "mail-" + threadCount.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** Removes characters that could break or inject lines in the email body; never returns blank. */
    static String sanitize(String userValue) {
        String cleaned = userValue == null ? "" : UNSAFE_TEXT.matcher(userValue).replaceAll("").trim();
        return cleaned.isEmpty() ? "usuario" : cleaned;
    }

    private static String describe(Duration duration) {
        long hours = duration.toHours();
        if (hours >= 1) {
            return hours == 1 ? "1 hora" : hours + " horas";
        }
        long minutes = duration.toMinutes();
        return minutes == 1 ? "1 minuto" : minutes + " minutos";
    }

    private static String layout(String greeting, String intro, String buttonLabel, String link,
                                 String expiryNote, String footer) {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <body style="margin:0;padding:24px;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"
                             style="max-width:480px;background:#ffffff;border-radius:12px;padding:32px;">
                        <tr><td>
                          <h1 style="margin:0 0 24px;font-size:22px;color:#111827;">Pytra</h1>
                          <p style="margin:0 0 12px;font-size:15px;">%s</p>
                          <p style="margin:0 0 24px;font-size:15px;line-height:1.5;">%s</p>
                          <p style="margin:0 0 24px;">
                            <a href="%s" style="display:inline-block;background:#724dce;color:#ffffff;text-decoration:none;padding:12px 20px;border-radius:8px;font-weight:bold;font-size:15px;">%s</a>
                          </p>
                          <p style="margin:0 0 8px;font-size:13px;color:#6b7280;">Si el botón no funciona, copia este enlace en tu navegador:</p>
                          <p style="margin:0 0 24px;font-size:13px;word-break:break-all;"><a href="%s" style="color:#724dce;">%s</a></p>
                          <p style="margin:0 0 8px;font-size:13px;color:#6b7280;">%s</p>
                          <p style="margin:0;font-size:13px;color:#6b7280;">%s</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(greeting, intro, link, buttonLabel, link, link, expiryNote, footer);
    }
}
