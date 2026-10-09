package com.estebanmm13.pytra_api.auth.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthEmailServiceTest {

    @Test
    void sanitizeRemovesLineBreaksAndControlCharacters() {
        assertEquals("EvilBcc: x", AuthEmailService.sanitize("Evil\r\nBcc: x"));
        assertEquals("ab", AuthEmailService.sanitize("a\u0000 b"));
        assertEquals("usuario", AuthEmailService.sanitize(" \n "));
        assertEquals("usuario", AuthEmailService.sanitize(null));
    }
}
