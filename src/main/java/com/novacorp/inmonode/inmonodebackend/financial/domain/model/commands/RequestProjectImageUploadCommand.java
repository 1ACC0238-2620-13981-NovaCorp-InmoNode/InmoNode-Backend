package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectImage;

/**
 * Asks for a presigned URL to upload a photo or render of a project straight to the file repository (US-57).
 */
public record RequestProjectImageUploadCommand(ProjectImage image) {
}
