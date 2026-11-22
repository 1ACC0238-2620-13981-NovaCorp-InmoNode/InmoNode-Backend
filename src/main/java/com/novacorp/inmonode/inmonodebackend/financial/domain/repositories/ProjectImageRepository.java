package com.novacorp.inmonode.inmonodebackend.financial.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectImage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The images registered in the gallery of each project (US-57).
 */
public interface ProjectImageRepository {

    /** Registers an uploaded image; an image id is registered only once, whatever its project. */
    void add(ProjectImage image, Instant registeredAt);

    /** The image registered with this id, in any project. */
    Optional<RegisteredImage> findByImageId(UUID imageId);

    /** The images of a project in the order they were registered. */
    List<RegisteredImage> findByProjectId(Long projectId);

    record RegisteredImage(ProjectImage image, Instant registeredAt) {
    }
}
