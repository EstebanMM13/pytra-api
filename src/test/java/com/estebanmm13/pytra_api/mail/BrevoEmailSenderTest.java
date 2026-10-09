package com.estebanmm13.pytra_api.mail;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpStatus.CREATED;

class BrevoEmailSenderTest {

    @Test
    void sendPostsTransactionalEmailToBrevo() {
        RestClient.Builder builder = BrevoEmailSender.configure(RestClient.builder(), "test-api-key");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BrevoEmailSender sender = new BrevoEmailSender(builder.build(), "no-reply@pytra.app", "Pytra");

        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "test-api-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sender.name").value("Pytra"))
                .andExpect(jsonPath("$.sender.email").value("no-reply@pytra.app"))
                .andExpect(jsonPath("$.to", hasSize(1)))
                .andExpect(jsonPath("$.to[0].email").value("user@example.com"))
                .andExpect(jsonPath("$.to[0].name").doesNotExist())
                .andExpect(jsonPath("$.subject").value("Subject"))
                .andExpect(jsonPath("$.htmlContent").value("<p>Hi</p>"))
                .andExpect(jsonPath("$.textContent").value("Hi"))
                .andRespond(withStatus(CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"messageId\":\"<id@smtp-relay.mailin.fr>\"}"));

        sender.send(new EmailMessage("user@example.com", "Subject", "<p>Hi</p>", "Hi"));

        server.verify();
    }
}
