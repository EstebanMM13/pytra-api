package com.estebanmm13.pytra_api.steamsync.service;

import com.estebanmm13.pytra_api.auth.security.TokenGenerator;
import com.estebanmm13.pytra_api.error.InvalidTokenException;
import com.estebanmm13.pytra_api.steamsync.model.SteamLink;
import com.estebanmm13.pytra_api.steamsync.model.SteamLinkState;
import com.estebanmm13.pytra_api.steamsync.repository.SteamLinkRepository;
import com.estebanmm13.pytra_api.steamsync.repository.SteamLinkStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SteamLinkServiceImpl implements SteamLinkService {

    private final SteamLinkStateRepository steamLinkStateRepository;
    private final SteamLinkRepository steamLinkRepository;
    private final TokenGenerator tokenGenerator;

    @Override
    public String createLinkState(Long userId) {
        String rawToken = tokenGenerator.generateTokenRaw();
        String tokenHash = tokenGenerator.hashToken(rawToken);

        SteamLinkState state = SteamLinkState.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        steamLinkStateRepository.save(state);

        return rawToken;
    }

    @Override
    public Long consumeLinkState(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidTokenException("Invalid Steam link state");
        }

        String tokenHash = tokenGenerator.hashToken(rawToken);
        SteamLinkState state = steamLinkStateRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Invalid Steam link state"));

        if (state.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Invalid Steam link state");
        }
        if (state.getConsumedAt() != null) {
            throw new InvalidTokenException("Invalid Steam link state");
        }

        state.setConsumedAt(LocalDateTime.now());
        steamLinkStateRepository.save(state);

        return state.getUserId();
    }

    @Override
    public SteamLink upsertLink(Long userId, String steamId64, String personaName) {
        Optional<SteamLink> existing = steamLinkRepository.findByUserId(userId);

        SteamLink steamLink = existing.orElseGet(() -> SteamLink.builder().userId(userId).build());
        steamLink.setSteamId(steamId64);
        steamLink.setPersonaName(personaName);
        steamLink.setLinkedAt(LocalDateTime.now());

        return steamLinkRepository.save(steamLink);
    }
}
