package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectImage;

/**
 * Adds to the gallery of a project an image the back office already uploaded to the file repository (US-57).
 */
public record RegisterProjectImageCommand(ProjectImage image) {
}
