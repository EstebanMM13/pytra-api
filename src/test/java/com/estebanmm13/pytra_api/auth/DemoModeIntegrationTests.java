package com.estebanmm13.pytra_api.auth;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.auth.security.DemoMode;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public read-only demo: token issuance, read access to the demo account only, and every write rejected. */
class DemoModeIntegrationTests extends AbstractIntegrationTest {

    /** Unstubbed it behaves as "demo disabled"; each test enables it for its own demo user. */
    @MockitoBean
    private DemoMode demoMode;

    @Autowired
    private GameRepository gameRepository;

    private User demoUser;
    private String ownerAuth;
    private Long demoGameId;

    @BeforeEach
    void createDemoAccount() throws Exception {
        demoUser = createVerifiedUser("demo");
        ownerAuth = bearer(demoUser);
        demoGameId = createGameVia(ownerAuth, "Demo Game", "");
    }

    private void enableDemo() {
        when(demoMode.isEnabled()).thenReturn(true);
        when(demoMode.getDemoUserId()).thenReturn(demoUser.getId());
        when(demoMode.isDemoUser(any())).thenAnswer(inv -> demoUser.getId().equals(inv.getArgument(0)));
    }

    private String demoAuth() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/demo"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    @Test
    void demoDisabledIs404() throws Exception {
        mockMvc.perform(post("/api/v1/auth/demo"))
                .andExpect(status().isNotFound());
    }

    @Test
    void demoTokenReadsTheDemoAccount() throws Exception {
        enableDemo();
        String demoAuth = demoAuth();

        assertThat(jwtService.isDemo(demoAuth.substring(7))).isTrue();
        performAs(demoAuth, get("/api/v1/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(demoGameId));
        performAs(demoAuth, get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(demoUser.getUsername()))
                // The demo account is public: its owner's email is never shown to visitors.
                .andExpect(jsonPath("$.email").value("demo@example.com"));
        performAs(ownerAuth, get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(demoUser.getEmail()));
    }

    @Test
    void demoTokenCannotReadOtherUsers() throws Exception {
        enableDemo();
        User other = createVerifiedUser("demoother");
        Long otherGameId = createGameVia(bearer(other), "Other Game", "");

        performAs(demoAuth(), get("/api/v1/games/{id}", otherGameId))
                .andExpect(status().isNotFound());
    }

    @Test
    void everyWriteWithDemoTokenIs403AndChangesNothing() throws Exception {
        enableDemo();
        String demoAuth = demoAuth();

        List<MockHttpServletRequestBuilder> writes = List.of(
                post("/api/v1/games"),
                put("/api/v1/games/{id}", demoGameId),
                delete("/api/v1/games/{id}", demoGameId),
                post("/api/v1/games/{id}/experiences", demoGameId),
                put("/api/v1/games/{id}/online-playtime", demoGameId),
                post("/api/v1/sagas"),
                post("/api/v1/genres"),
                put("/api/v1/stats/years/2024/note"),
                patch("/api/v1/users/me"),
                delete("/api/v1/users/me"),
                post("/api/v1/integrations/steam/connect-token"),
                post("/api/v1/integrations/steam/sync"),
                delete("/api/v1/integrations/steam/link"),
                // Not a write, but it consumes the per-user export rate limit shared by every visitor.
                get("/api/v1/users/me/export"));

        for (MockHttpServletRequestBuilder write : writes) {
            performAs(demoAuth, write, "{\"name\": \"Hacked\", \"category\": \"SINGLEPLAYER\", \"confirm\": \"DELETE\"}")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("DEMO_READ_ONLY"));
        }

        assertThat(gameRepository.findById(demoGameId)).get()
                .extracting(game -> game.getName()).isEqualTo("Demo Game");
        assertThat(userRepository.existsById(demoUser.getId())).isTrue();
    }

    @Test
    void demoTokenStopsWorkingWhenDemoIsDisabled() throws Exception {
        enableDemo();
        String demoAuth = demoAuth();

        when(demoMode.isDemoUser(any())).thenReturn(false);
        performAs(demoAuth, get("/api/v1/games"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void normalTokensAreUnaffected() throws Exception {
        enableDemo();

        // Same account, normal (non-demo) token: writes still work.
        performAs(ownerAuth, put("/api/v1/games/{id}", demoGameId),
                        "{\"name\": \"Renamed\", \"category\": \"SINGLEPLAYER\"}")
                .andExpect(status().isOk());
        createGameVia(bearer(createVerifiedUser("demonormal")), "Normal Game", "");
    }
}
