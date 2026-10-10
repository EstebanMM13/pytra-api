package com.estebanmm13.pytra_api.auth.security;

import com.estebanmm13.pytra_api.auth.model.Role;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.auth.repository.UserRepository;
import com.estebanmm13.pytra_api.auth.validation.RegistrationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Google sign-in must never reach (or link a Google identity to) the demo-only account. */
class GoogleOidcUserServiceDemoUserTest {

    private static final long DEMO_ID = 7L;
    private static final String EMAIL = "owner@pytra.test";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final GoogleOidcUserService service =
            new GoogleOidcUserService(userRepository, mock(RegistrationPolicy.class), new DemoMode(String.valueOf(DEMO_ID)));

    private static OidcUser googleIdentity() {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getSubject()).thenReturn("google-sub");
        when(oidcUser.getEmail()).thenReturn(EMAIL);
        when(oidcUser.getEmailVerified()).thenReturn(true);
        return oidcUser;
    }

    private static User user(long id, String googleId) {
        return User.builder().id(id).email(EMAIL).username("demo").usernameDisplay("demo")
                .googleId(googleId).passwordHash("hash").emailVerified(true)
                .createdAt(LocalDateTime.now()).role(Role.USER).build();
    }

    @Test
    void demoAccountAlreadyLinkedToGoogleIsRefused() {
        when(userRepository.findByGoogleId("google-sub")).thenReturn(Optional.of(user(DEMO_ID, "google-sub")));

        assertThatThrownBy(() -> service.resolveUser(googleIdentity()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> assertThat(OAuth2LoginFailureHandler.clientErrorFor(
                        ((OAuth2AuthenticationException) e).getError().getErrorCode()))
                        .isEqualTo(OAuth2LoginFailureHandler.GENERIC_ERROR));
    }

    @Test
    void demoAccountMatchedByEmailIsRefusedAndNotLinked() {
        User demo = user(DEMO_ID, null);
        when(userRepository.findByGoogleId("google-sub")).thenReturn(Optional.empty());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(demo));

        assertThatThrownBy(() -> service.resolveUser(googleIdentity()))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(demo.getGoogleId()).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    void otherAccountsStillSignIn() {
        when(userRepository.findByGoogleId("google-sub")).thenReturn(Optional.of(user(8L, "google-sub")));

        assertThat(service.resolveUser(googleIdentity()).getDomainUser().getId()).isEqualTo(8L);
    }
}
