package com.estebanmm13.pytra_api.auth.security;

import com.estebanmm13.pytra_api.auth.model.ExchangeCodeToken;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.auth.repository.ExchangeCodeTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ExchangeCodeIssuer {

    private final TokenGenerator tokenGenerator;
    private final ExchangeCodeTokenRepository exchangeCodeTokenRepository;

    public String issueFor(User user) {
        String rawCode = tokenGenerator.generateTokenRaw();
        String codeHash = tokenGenerator.hashToken(rawCode);

        ExchangeCodeToken exchangeCodeToken = ExchangeCodeToken.builder()
                .user(user)
                .tokenHash(codeHash)
                .expiresAt(LocalDateTime.now().plusMinutes(1))
                .build();
        exchangeCodeTokenRepository.save(exchangeCodeToken);

        return rawCode;
    }
}
