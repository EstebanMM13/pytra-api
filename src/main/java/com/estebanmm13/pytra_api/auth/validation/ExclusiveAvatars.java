package com.estebanmm13.pytra_api.auth.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owners of the single-owner avatar presets ({@link AvatarPolicy#EXCLUSIVE_KEYS}), from
 * {@code app.avatars.exclusive-owners} ({@code EXCLUSIVE_AVATAR_OWNERS}): comma-separated {@code key:userId} pairs.
 * Ownership is bound to the immutable user id because usernames can be changed (PATCH /users/me), so a username
 * binding could be taken over by whoever claims the name next. Empty = no exclusive preset is selectable.
 * Pairs naming a key outside {@link AvatarPolicy#EXCLUSIVE_KEYS} or with a non-numeric id are ignored.
 */
@Slf4j
@Component
public class ExclusiveAvatars {

    private final Map<String, Long> ownerIds;

    public ExclusiveAvatars(@Value("${app.avatars.exclusive-owners:}") String ownersRaw) {
        this.ownerIds = parse(ownersRaw);
        log.info("Exclusive avatars: {} of {} presets have an owner", ownerIds.size(), AvatarPolicy.EXCLUSIVE_KEYS.size());
    }

    /** True when {@code userId} may store {@code key}: any shared preset, or an exclusive preset it owns. */
    public boolean isAllowed(String key, Long userId) {
        if (AvatarPolicy.isValid(key)) {
            return true;
        }
        return key != null && userId != null && Objects.equals(ownerIds.get(key), userId);
    }

    /** Exclusive keys owned by {@code userId}, sorted; empty for everyone else (exposed on GET /users/me). */
    public List<String> ownedBy(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return ownerIds.entrySet().stream()
                .filter(e -> userId.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    static Map<String, Long> parse(String raw) {
        Map<String, Long> owners = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        for (String pair : raw.split(",")) {
            String[] parts = pair.trim().split(":");
            if (parts.length != 2 || !AvatarPolicy.EXCLUSIVE_KEYS.contains(parts[0].trim())) {
                log.warn("Ignoring exclusive avatar owner entry with an unknown key or bad format");
                continue;
            }
            try {
                owners.put(parts[0].trim(), Long.valueOf(parts[1].trim()));
            } catch (NumberFormatException e) {
                log.warn("Ignoring exclusive avatar owner entry with a non-numeric user id");
            }
        }
        return Map.copyOf(owners);
    }
}
