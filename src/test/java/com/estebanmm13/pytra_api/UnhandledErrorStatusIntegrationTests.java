package com.estebanmm13.pytra_api;

import com.estebanmm13.pytra_api.auth.model.Role;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.auth.repository.UserRepository;
import com.estebanmm13.pytra_api.auth.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An unhandled exception in an authorized request must reach the client as a 500, not a 401: the web
 * client logs the user out on 401. Needs a real server, since MockMvc never performs the error forward.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, UnhandledErrorStatusIntegrationTests.FailingController.class})
class UnhandledErrorStatusIntegrationTests {

    @TestConfiguration(proxyBeanMethods = false)
    @RestController
    static class FailingController {
        @GetMapping("/api/v1/test-only/boom")
        String boom() {
            throw new IllegalStateException("boom");
        }
    }

    @Autowired
    private Environment environment;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void unhandledExceptionInAnAuthorizedRequestIsA500NotA401() throws Exception {
        String username = ("boom" + UUID.randomUUID().toString().substring(0, 8)).toLowerCase();
        User user = userRepository.save(User.builder()
                .email(username + "@example.test").username(username).usernameDisplay(username)
                .emailVerified(true).createdAt(LocalDateTime.now()).role(Role.USER).build());
        String token = jwtService.generateTokenWithRole(user.getUsername(), user.getRole().name(), user.getId());
        String port = environment.getProperty("local.server.port");

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/test-only/boom"))
                        .header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(500);
    }

    @Test
    void unauthenticatedRequestIsStillA401() throws Exception {
        String port = environment.getProperty("local.server.port");

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/test-only/boom")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }
}
