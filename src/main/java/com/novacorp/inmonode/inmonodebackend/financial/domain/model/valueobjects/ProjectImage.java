package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import java.util.UUID;

/**
 * A photo or render of a project for the gallery of its page (US-57), kept in the file repository (US-33). It is
 * stored under a key derived from its project and its own id, so a retried upload writes the same object instead of
 * leaving orphans.
 *
 * @param projectId   the project it shows
 * @param imageId     id the back office generated for the image
 * @param contentType {@code image/jpeg} or {@code image/png}, in lower case
 * @param sizeBytes   exact size of the file, up to 5 MB
 */
public record ProjectImage(Long projectId, UUID imageId, String contentType, long sizeBytes) {

    public ProjectImage {
        if (projectId == null || imageId == null) {
            throw new IllegalArgumentException("a project image needs its project and its id");
        }
        contentType = new ImageFile(contentType, sizeBytes).contentType();
    }

    /** Where the file lives in the file repository: {@code projects/{projectId}/images/{imageId}.{ext}}. */
    public String objectKey() {
        return "projects/%d/images/%s.%s".formatted(projectId, imageId,
                new ImageFile(contentType, sizeBytes).extension());
    }
}
