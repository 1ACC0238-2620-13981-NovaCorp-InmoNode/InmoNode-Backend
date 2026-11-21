package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IdentityDocumentTest {

    private static final UUID TRANSACTION = UUID.fromString("5f1c2d3e-0000-4000-8000-000000000001");
    private static final UUID DOCUMENT = UUID.fromString("9a8b7c6d-0000-4000-8000-000000000002");

    @Test
    void aPhotoIsStoredUnderItsReservationAndItsOwnId() {
        assertEquals("identity-documents/%s/%s.jpg".formatted(TRANSACTION, DOCUMENT),
                new IdentityDocument(TRANSACTION, DOCUMENT, "image/jpeg", 640_000).objectKey());
        assertEquals("identity-documents/%s/%s.png".formatted(TRANSACTION, DOCUMENT),
                new IdentityDocument(TRANSACTION, DOCUMENT, "image/png", 640_000).objectKey());
    }

    @Test
    void theContentTypeIsComparedWithoutCaseOrSpaces() {
        assertEquals("image/jpeg", new IdentityDocument(TRANSACTION, DOCUMENT, " Image/JPEG ", 1).contentType());
    }

    @Test
    void onlyJpegOrPngPhotosUpToFiveMegabytesAreAccepted() {
        assertDoesNotThrow(() -> new IdentityDocument(TRANSACTION, DOCUMENT, "image/png", IdentityDocument.MAX_SIZE_BYTES));
        assertThrows(IllegalArgumentException.class,
                () -> new IdentityDocument(TRANSACTION, DOCUMENT, "application/pdf", 1000));
        assertThrows(IllegalArgumentException.class, () -> new IdentityDocument(TRANSACTION, DOCUMENT, null, 1000));
        assertThrows(IllegalArgumentException.class, () -> new IdentityDocument(TRANSACTION, DOCUMENT, "image/jpeg", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new IdentityDocument(TRANSACTION, DOCUMENT, "image/jpeg", IdentityDocument.MAX_SIZE_BYTES + 1));
        assertThrows(IllegalArgumentException.class, () -> new IdentityDocument(null, DOCUMENT, "image/jpeg", 1000));
        assertThrows(IllegalArgumentException.class, () -> new IdentityDocument(TRANSACTION, null, "image/jpeg", 1000));
    }
}
