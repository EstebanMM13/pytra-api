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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.http.MediaType;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    /** Performs the request with the given Authorization header value. */
    protected ResultActions performAs(String authorization, MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(builder.header("Authorization", authorization));
    }

    /** Performs the request with the given Authorization header value and a JSON body. */
    protected ResultActions performAs(String authorization, MockHttpServletRequestBuilder builder,
                                      String jsonBody) throws Exception {
        return mockMvc.perform(builder
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody));
    }

    /** Creates a SINGLEPLAYER game through the API; {@code extraJson} is appended to the body (e.g. {@code , "sagaId": 1}). */
    protected Long createGameVia(String authorization, String name, String extraJson) throws Exception {
        String body = "{\"name\": \"" + name + "\", \"category\": \"SINGLEPLAYER\"" + extraJson + "}";
        return readId(performAs(authorization, post("/api/v1/games"), body)
                .andExpect(status().isCreated()).andReturn());
    }

    /** Logs an experience through the API; {@code json} is the full request body. */
    protected Long createExperienceVia(String authorization, Long gameId, String json) throws Exception {
        return readId(performAs(authorization, post("/api/v1/games/{gameId}/experiences", gameId), json)
                .andExpect(status().isCreated()).andReturn());
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

    /** Experience request body; null values are omitted. */
    protected static String run(String label, String status, Integer rating, Double hours, Integer year,
                                 String startDate, String endDate, String extraJson) {
        StringBuilder json = new StringBuilder("{\"runLabel\": \"" + label + "\", \"status\": \"" + status
                + "\", \"platform\": \"PC\"");
        if (rating != null) json.append(", \"rating\": ").append(rating);
        if (hours != null) json.append(", \"hours\": ").append(hours);
        if (year != null) json.append(", \"year\": ").append(year);
        if (startDate != null) json.append(", \"startDate\": \"").append(startDate).append('"');
        if (endDate != null) json.append(", \"endDate\": \"").append(endDate).append('"');
        return json.append(extraJson).append('}').toString();
    }
}
