package com.novacorp.inmonode.inmonodebackend.catalog.domain.model.aggregates;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProspectTest {

    private static final UUID ID = UUID.randomUUID();
    private static final Instant REGISTERED_AT = Instant.parse("2026-10-08T09:00:00Z");

    @Test
    void registeredProspectHasItsDataNormalized() {
        var prospect = Prospect.register(ID, 7L, " 001234567x ", "  Ana Quispe  ", " ", REGISTERED_AT);

        assertEquals(ID, prospect.getProspectId());
        assertEquals(7L, prospect.getAgentId());
        assertEquals("001234567X", prospect.getDocument());
        assertEquals("Ana Quispe", prospect.getFullName());
        assertNull(prospect.getPhone(), "a blank phone is no phone");
        assertEquals(REGISTERED_AT, prospect.getRegisteredAt());
        assertTrue(prospect.isRegisteredBy(7L));
        assertFalse(prospect.isRegisteredBy(8L));
    }

    @Test
    void identityDocumentIsRequiredAndWellFormed() {
        assertMessage("document is required", () -> register(null, "Ana", null));
        assertMessage("document is required", () -> register("  ", "Ana", null));
        assertMessage("8 to 12", () -> register("1234567", "Ana", null));
        assertMessage("8 to 12", () -> register("1234567890123", "Ana", null));
        assertMessage("8 to 12", () -> register("1234-5678", "Ana", null));
        assertDoesNotThrow(() -> register("12345678", "Ana", null));
    }

    @Test
    void nameIsRequiredAndPhoneMustLookLikeOne() {
        assertMessage("full name is required", () -> register("12345678", " ", null));
        assertMessage("at most", () -> register("12345678", "A".repeat(Prospect.MAX_FULL_NAME_LENGTH + 1), null));
        assertMessage("phone", () -> register("12345678", "Ana", "call me"));
        assertDoesNotThrow(() -> register("12345678", "Ana", "+51 987-654-321"));
    }

    @Test
    void contactInfoChangesButTheDocumentStays() {
        var prospect = register("12345678", "Ana", "987654321");

        prospect.updateContactInfo("Ana Quispe Mamani", null);

        assertEquals("Ana Quispe Mamani", prospect.getFullName());
        assertNull(prospect.getPhone());
        assertEquals("12345678", prospect.getDocument());
    }

    private static Prospect register(String document, String fullName, String phone) {
        return Prospect.register(ID, 7L, document, fullName, phone, REGISTERED_AT);
    }

    private static void assertMessage(String expected, Executable action) {
        var error = assertThrows(IllegalArgumentException.class, action);
        assertTrue(error.getMessage().contains(expected), error.getMessage());
    }
}
