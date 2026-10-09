package com.estebanmm13.pytra_api.auth.validation;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationPolicyTest {

    @Test
    void emptyOrBlankListMeansOpenRegistration() {
        for (String raw : new String[]{null, "", "   ", " , ,"}) {
            RegistrationPolicy policy = new RegistrationPolicy(raw);
            assertTrue(policy.isOpen(), "expected open for: " + raw);
            assertTrue(policy.isAllowed("anyone@example.com"));
        }
    }

    @Test
    void parseTrimsNormalizesAndDropsEmpties() {
        assertEquals(
                Set.of("alice@example.com", "bob@example.org"),
                RegistrationPolicy.parse("  Alice@Example.com ,, BOB@example.ORG ,  "));
    }

    @Test
    void inviteOnlyAllowsListedEmailsCaseInsensitively() {
        RegistrationPolicy policy = new RegistrationPolicy("alice@example.com,Bob@Example.org");

        assertFalse(policy.isOpen());
        assertTrue(policy.isAllowed("alice@example.com"));
        assertTrue(policy.isAllowed("  ALICE@EXAMPLE.COM "));
        assertTrue(policy.isAllowed("bob@example.org"));
    }

    @Test
    void inviteOnlyRejectsUnlistedAndNullEmails() {
        RegistrationPolicy policy = new RegistrationPolicy("alice@example.com");

        assertFalse(policy.isAllowed("mallory@example.com"));
        assertFalse(policy.isAllowed("alice@example.com.evil"));
        assertFalse(policy.isAllowed(""));
        assertFalse(policy.isAllowed(null));
    }
}
