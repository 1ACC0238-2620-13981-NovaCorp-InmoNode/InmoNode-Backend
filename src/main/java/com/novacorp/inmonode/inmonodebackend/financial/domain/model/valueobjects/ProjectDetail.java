package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * A project as its page shows it: its data and the gallery of photos and renders (US-57).
 *
 * @param gallery the images in the order they were registered; empty when none was loaded yet, so the page shows
 *                its fallback image (US-57, Scenario 3)
 */
public record ProjectDetail(Project project, List<GalleryImage> gallery) {

    public ProjectDetail {
        gallery = List.copyOf(gallery);
    }

    /**
     * @param url       presigned link to the file, valid until {@code expiresAt}
     * @param expiresAt when that link stops working; read the project again for new ones
     */
    public record GalleryImage(ProjectImage image, URI url, Instant expiresAt) {
    }
}
