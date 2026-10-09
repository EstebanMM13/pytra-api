package com.estebanmm13.pytra_api;

import com.estebanmm13.pytra_api.auth.model.Role;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.auth.repository.UserRepository;
import com.estebanmm13.pytra_api.auth.security.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Shared setup for full-context integration tests: one Spring context and one Postgres
 * Testcontainer (via {@link TestcontainersConfiguration}'s {@code @ServiceConnection}) reused
 * by every subclass thanks to Spring's test context cache.
 *
 * <p>The database is NOT wiped between tests, so every test must create its own users with
 * {@link #createVerifiedUser(String)} (unique email/username) instead of relying on a clean slate.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected JwtService jwtService;

    /** Persists a verified, password-less USER with a unique email/username derived from {@code prefix}. */
    protected User createVerifiedUser(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = (prefix + suffix).toLowerCase();
        return userRepository.save(User.builder()
                .email(username + "@example.test")
                .username(username)
                .usernameDisplay(username)
                .emailVerified(true)
                .createdAt(LocalDateTime.now())
                .role(Role.USER)
                .build());
    }

    /** Value for the Authorization header, built exactly like the login flow does. */
    protected String bearer(User user) {
        return "Bearer " + jwtService.generateTokenWithRole(user.getUsername(), user.getRole().name(), user.getId());
    }

    protected static Long readId(MvcResult result) throws UnsupportedEncodingException {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    /**
     * Non-null ids found at {@code path} (e.g. {@code "$[*].id"}) as longs, so they compare cleanly with
     * entity ids regardless of whether JSON numbers were parsed as Integer or Long.
     */
    protected static List<Long> readIds(MvcResult result, String path) throws UnsupportedEncodingException {
        List<Number> ids = JsonPath.read(result.getResponse().getContentAsString(), path);
        return ids.stream().filter(Objects::nonNull).map(Number::longValue).toList();
    }
}
