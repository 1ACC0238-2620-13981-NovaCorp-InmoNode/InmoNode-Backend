package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectImage;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectImageRegistration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectImageUpload;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ProjectImageFileResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ProjectImageRegistrationResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ProjectImageUploadResource;

public final class ProjectImageResourceAssembler {

    private ProjectImageResourceAssembler() {}

    /** @throws IllegalArgumentException for another type or a size out of range, answered as 400 */
    public static ProjectImage toImage(Long projectId, ProjectImageFileResource resource) {
        return new ProjectImage(projectId, resource.imageId(), resource.contentType(), resource.sizeBytes());
    }

    public static ProjectImageUploadResource toResourceFromUpload(ProjectImageUpload upload) {
        return new ProjectImageUploadResource(upload.uploadUrl().toString(), upload.objectKey(), upload.expiresAt(),
                upload.headers());
    }

    public static ProjectImageRegistrationResource toResourceFromRegistration(ProjectImageRegistration registration) {
        var image = registration.image();
        return new ProjectImageRegistrationResource(image.projectId(), image.imageId(), image.contentType(),
                image.sizeBytes(), registration.registeredAt(), registration.result().name());
    }
}
