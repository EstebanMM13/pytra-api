package com.estebanmm13.pytra_api.auth.security;

import com.estebanmm13.pytra_api.auth.model.PytraOidcUser;
import com.estebanmm13.pytra_api.auth.model.Role;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.auth.repository.UserRepository;
import com.estebanmm13.pytra_api.error.EmailNotVerifiedException;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class GoogleOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final UserRepository userRepository;
    private final OidcUserService oidcUserService = new OidcUserService();

    public GoogleOidcUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public @Nullable OidcUser loadUser(OidcUserRequest oidcUserRequest) throws OAuth2AuthenticationException {

        OidcUser oidcUser = oidcUserService.loadUser(oidcUserRequest);
        String googleId = oidcUser.getSubject();
        String email = oidcUser.getEmail().toLowerCase();
        boolean emailVerified = oidcUser.getEmailVerified();

        Optional<User> user = userRepository.findByGoogleId(googleId);

        if (user.isPresent()) {
            return new PytraOidcUser(oidcUser,user.get());
        }
        user = userRepository.findByEmail(email);
        if (user.isPresent() && emailVerified) {
            user.get().setGoogleId(googleId);
            if (!user.get().getEmailVerified()){
                user.get().setEmailVerified(true);
            }
            userRepository.save(user.get());
            return new PytraOidcUser(oidcUser,user.get());
        }if (user.isPresent() && !emailVerified) {
            throw new EmailNotVerifiedException("User" + user.get().getEmail() + " is not verified");
        }
        else {
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
        String base = email.split("@")[0];
        String username = base;
        int contador = 1;
        while (userRepository.existsByUsername(username)) {
            username = base + contador;
            contador++;
        }
        return username;
    }
}
