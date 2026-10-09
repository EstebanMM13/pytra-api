package com.estebanmm13.pytra_api.mail;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Sends email through Brevo's transactional HTTP API (SMTP is blocked on the hosting platform).
 * See https://developers.brevo.com/reference/sendtransacemail
 */
@Slf4j
public class BrevoEmailSender implements EmailSender {

    static final String BASE_URL = "https://api.brevo.com/v3";
    static final String SEND_PATH = "/smtp/email";

    private final RestClient restClient;
    private final String fromEmail;
    private final String fromName;

    public BrevoEmailSender(String apiKey, String fromEmail, String fromName) {
        this(defaultRestClient(apiKey), fromEmail, fromName);
    }

    BrevoEmailSender(RestClient restClient, String fromEmail, String fromName) {
        this.restClient = restClient;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
    }

    @Override
    public void send(EmailMessage message) {
        restClient.post()
                .uri(SEND_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildRequest(message))
                .retrieve()
                .toBodilessEntity();
        log.info("Email '{}' sent via Brevo", message.subject());
    }

    BrevoSendRequest buildRequest(EmailMessage message) {
        return new BrevoSendRequest(
                new BrevoContact(fromName, fromEmail),
                List.of(new BrevoContact(null, message.to())),
                message.subject(),
                message.htmlContent(),
                message.textContent()
        );
    }

    private static RestClient defaultRestClient(String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return configure(RestClient.builder().requestFactory(requestFactory), apiKey).build();
    }

    static RestClient.Builder configure(RestClient.Builder builder, String apiKey) {
        return builder
                .baseUrl(BASE_URL)
                .defaultHeader("api-key", apiKey)
                .defaultHeader("accept", MediaType.APPLICATION_JSON_VALUE);
    }

    record BrevoSendRequest(BrevoContact sender, List<BrevoContact> to, String subject,
                            String htmlContent, String textContent) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BrevoContact(String name, String email) {
    }
}
