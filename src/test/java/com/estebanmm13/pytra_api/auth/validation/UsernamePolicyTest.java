package com.estebanmm13.pytra_api.auth.validation;

import com.estebanmm13.pytra_api.auth.dto.register.RegisterRequestDto;
import com.estebanmm13.pytra_api.auth.dto.user.UpdateUsernameRequestDto;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsernamePolicyTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void normalizeTrimsAndLowercasesIndependentlyOfLocale() {
        assertEquals("title", UsernamePolicy.normalize("  TITLE "));
        assertEquals("user@x.com", UsernamePolicy.normalizeEmail(" User@X.com "));
    }

    @Test
    void registerDtoAcceptsValidUsernamesAndTrimsThem() {
        RegisterRequestDto dto = registerDto("  Esteban_M.13-x  ");
        assertEquals("Esteban_M.13-x", dto.getUsername());
        assertTrue(validator.validateProperty(dto, "username").isEmpty());
    }

    @Test
    void registerDtoRejectsEmailsAndOtherInvalidUsernames() {
        for (String invalid : new String[]{"victim@x.com", "ab", "a".repeat(31), "has space", "tab\tname",
                "ñandú", "line\nbreak", "", "   "}) {
            assertFalse(validator.validateProperty(registerDto(invalid), "username").isEmpty(),
                    () -> "should reject: " + invalid);
        }
    }

    @Test
    void updateDtoUsesTheSameRules() {
        UpdateUsernameRequestDto dto = new UpdateUsernameRequestDto();
        dto.setUsername("victim@x.com");
        assertFalse(validator.validate(dto).isEmpty());
        dto.setUsername(" new.name ");
        assertTrue(validator.validate(dto).isEmpty());
    }

    @Test
    void generatedUsernamesFromEmailAlwaysMatchThePattern() {
        assertEquals("john.doe", UsernamePolicy.generateFromEmail("John.Doe@gmail.com", taken()));
        assertEquals("johndoe", UsernamePolicy.generateFromEmail("john+doe@gmail.com", taken()));
        assertEquals("ab0", UsernamePolicy.generateFromEmail("ab@x.com", taken()));
        assertEquals("user", UsernamePolicy.generateFromEmail("ñ+@x.com", taken()));

        String longName = UsernamePolicy.generateFromEmail("a".repeat(64) + "@x.com", taken());
        assertEquals(25, longName.length());
        assertTrue(UsernamePolicy.NORMALIZED_PATTERN.matcher(longName).matches());
    }

    @Test
    void generatedUsernameAddsSuffixOnCollisionAndStaysWithinMaxLength() {
        String base = "a".repeat(25);
        String generated = UsernamePolicy.generateFromEmail("a".repeat(40) + "@x.com", taken(base, base + "1"));
        assertEquals(base + "2", generated);
        assertTrue(UsernamePolicy.isValid(generated));
        assertEquals("user1", UsernamePolicy.generateFromEmail("@x.com", taken("user")));
    }

    private static RegisterRequestDto registerDto(String username) {
        RegisterRequestDto dto = new RegisterRequestDto();
        dto.setUsername(username);
        dto.setEmail("user@example.com");
        dto.setPassword("password123");
        return dto;
    }

    private static java.util.function.Predicate<String> taken(String... names) {
        Set<String> set = Set.of(names);
        return set::contains;
    }
}
