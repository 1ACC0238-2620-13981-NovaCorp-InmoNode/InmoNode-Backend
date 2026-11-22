package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProjectImageTest {

    private static final UUID IMAGE = UUID.fromString("7d0b4b38-5f4e-4a65-9d0e-2c7a1f3e9b11");

    @Test
    void itIsStoredUnderItsProjectWithTheExtensionOfItsType() {
        assertEquals("projects/12/images/%s.jpg".formatted(IMAGE),
                new ProjectImage(12L, IMAGE, "image/jpeg", 2048).objectKey());
        assertEquals("projects/12/images/%s.png".formatted(IMAGE),
                new ProjectImage(12L, IMAGE, " Image/PNG ", 2048).objectKey());
        assertEquals("image/png", new ProjectImage(12L, IMAGE, " Image/PNG ", 2048).contentType());
    }

    @Test
    void onlyJpegOrPngFilesUpToFiveMegabytesAreAccepted() {
        assertDoesNotThrow(() -> new ProjectImage(12L, IMAGE, "image/png", ImageFile.MAX_SIZE_BYTES));
        assertThrows(IllegalArgumentException.class, () -> new ProjectImage(12L, IMAGE, "image/webp", 2048));
        assertThrows(IllegalArgumentException.class, () -> new ProjectImage(12L, IMAGE, null, 2048));
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectImage(12L, IMAGE, "image/jpeg", ImageFile.MAX_SIZE_BYTES + 1));
        assertThrows(IllegalArgumentException.class, () -> new ProjectImage(12L, IMAGE, "image/jpeg", 0));
    }

    @Test
    void itNeedsItsProjectAndItsId() {
        assertThrows(IllegalArgumentException.class, () -> new ProjectImage(null, IMAGE, "image/jpeg", 2048));
        assertThrows(IllegalArgumentException.class, () -> new ProjectImage(12L, null, "image/jpeg", 2048));
    }
}
