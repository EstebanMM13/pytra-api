package com.estebanmm13.pytra_api.auth.controller;

import com.estebanmm13.pytra_api.auth.dto.user.CurrentUserResponseDto;
import com.estebanmm13.pytra_api.auth.security.AuthenticatedUser;
import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import com.estebanmm13.pytra_api.auth.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The demo account is public: GET /users/me must not expose its owner's email to demo visitors. */
class UserControllerDemoRedactionTest {

    private final UserService userService = mock(UserService.class);
    private final CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService, currentUserResolver)).build();
        when(userService.getCurrentUser(5L)).thenReturn(new CurrentUserResponseDto(
                "owner", "Owner", "owner@real-mail.test", true, true, LocalDateTime.of(2025, 1, 1, 0, 0), "fox"));
    }

    private void signedInAs(boolean demo) {
        when(currentUserResolver.getCurrentUser()).thenReturn(new AuthenticatedUser("owner", 5L, null, demo));
        when(currentUserResolver.getCurrentUserId()).thenReturn(5L);
    }

    @Test
    void demoSessionGetsAPlaceholderEmail() throws Exception {
        signedInAs(true);

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(UserController.DEMO_EMAIL_PLACEHOLDER))
                .andExpect(jsonPath("$.username").value("owner"))
                .andExpect(jsonPath("$.usernameDisplay").value("Owner"))
                .andExpect(jsonPath("$.avatar").value("fox"))
                .andExpect(jsonPath("$.hasPassword").value(true));
    }

    @Test
    void normalSessionGetsTheRealEmail() throws Exception {
        signedInAs(false);

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@real-mail.test"));
    }
}
