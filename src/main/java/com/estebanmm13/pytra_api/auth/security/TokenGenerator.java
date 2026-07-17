package com.estebanmm13.pytra_api.auth.security;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// Generador compartido de tokens de un solo uso (verificación de email, reset de password).
@Service
public class TokenGenerator {

    // SecureRandom (no Random): fuente de entropía criptográfica, no predecible aunque se conozca la seed.
    // URL-safe: evita '+', '/', '=' que necesitarían URL-encoding en un query param (?token=...).
    public String generateTokenRaw() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[32]; // 256 bits, entropía suficiente para no ser adivinable por fuerza bruta
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Hash determinista y rápido (no BCrypt): el token ya tiene alta entropía por sí solo,
    // y necesitamos poder buscarlo en la BD por igualdad (SELECT ... WHERE token_hash = ?).
    public String hashToken(String rawToken) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = messageDigest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 siempre existe en la JVM; si esto salta, es un problema del entorno, no de datos de entrada.
            throw new RuntimeException(e);
        }
    }

}
