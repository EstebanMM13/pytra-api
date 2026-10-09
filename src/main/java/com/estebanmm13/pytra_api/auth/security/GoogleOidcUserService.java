package com.estebanmm13.pytra_api.auth.security;

import com.estebanmm13.pytra_api.auth.model.PytraOidcUser;
import com.estebanmm13.pytra_api.auth.model.Role;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.auth.repository.UserRepository;
import com.estebanmm13.pytra_api.auth.validation.RegistrationPolicy;
import com.estebanmm13.pytra_api.auth.validation.UsernamePolicy;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class GoogleOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    /** OAuth2 error code for a sign-up blocked by the invite allowlist; also the client callback error value. */
    public static final String REGISTRATION_CLOSED_ERROR_CODE = "registration_closed";

    private final UserRepository userRepository;
    private final RegistrationPolicy registrationPolicy;
    private final OidcUserService oidcUserService = new OidcUserService();

    public GoogleOidcUserService(UserRepository userRepository, RegistrationPolicy registrationPolicy) {
        this.userRepository = userRepository;
        this.registrationPolicy = registrationPolicy;
    }

    @Override
    public @Nullable OidcUser loadUser(OidcUserRequest oidcUserRequest) throws OAuth2AuthenticationException {

        OidcUser oidcUser = oidcUserService.loadUser(oidcUserRequest);
        String googleId = oidcUser.getSubject();
        String email = UsernamePolicy.normalizeEmail(oidcUser.getEmail());
        boolean emailVerified = Boolean.TRUE.equals(oidcUser.getEmailVerified());

        Optional<User> user = userRepository.findByGoogleId(googleId);

        if (user.isPresent()) {
            return new PytraOidcUser(oidcUser,user.get());
        }
        user = userRepository.findByEmail(email);
        if (user.isPresent() && emailVerified) {
            User existing = user.get();
            existing.setGoogleId(googleId);
            if (!existing.getEmailVerified()) {
                // The local account never proved ownership of this email; Google just did.
                // Drop the password so whoever registered with this email first (possibly an
                // attacker pre-registering it) can no longer sign in with it.
                existing.setEmailVerified(true);
                existing.setPasswordHash(null);
            }
            userRepository.save(existing);
            return new PytraOidcUser(oidcUser,user.get());
        }
        if (!emailVerified) {
            // Never create or link an account from an email Google has not verified:
            // it would let someone claim an address they do not own.
            throw new OAuth2AuthenticationException(new OAuth2Error("email_not_verified"));
        } else {
            if (!registrationPolicy.isAllowed(email)) {
                // Only brand-new accounts are gated; existing users matched above always sign in.
                throw new OAuth2AuthenticationException(new OAuth2Error(REGISTRATION_CLOSED_ERROR_CODE));
            }
            String usernameValido = devolverUsernameValido(email);
            User newUser = User.builder()
                    .username(usernameValido)
                    .email(email)
                    .usernameDisplay(usernameValido)
                    .googleId(googleId)
                    .passwordHash(null)
                    .emailVerified(true)
                    .createdAt(LocalDateTime.now())
                    .role(Role.USER)
                    .build();
            userRepository.save(newUser);
            return new PytraOidcUser(oidcUser,newUser);
        }
    }

    private String devolverUsernameValido(String email){
        return UsernamePolicy.generateFromEmail(email, userRepository::existsByUsername);
    }
}
