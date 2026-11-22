package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RegisterProjectImageCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RequestProjectImageUploadCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectImage;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectImageRegistration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectImageUpload;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectImageRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectImageRepository.RegisteredImage;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ProjectImageCommandService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Service
public class ProjectImageCommandServiceImpl implements ProjectImageCommandService {

    /** Like the other uploads: the presigned URL is valid for 10 minutes. */
    static final Duration UPLOAD_URL_VALIDITY = Duration.ofMinutes(10);

    private final ProjectRepository projectRepository;
    private final ProjectImageRepository projectImageRepository;
    private final ObjectStorage objectStorage;
    private final Clock clock;

    public ProjectImageCommandServiceImpl(ProjectRepository projectRepository,
                                          ProjectImageRepository projectImageRepository, ObjectStorage objectStorage,
                                          Clock clock) {
        this.projectRepository = projectRepository;
        this.projectImageRepository = projectImageRepository;
        this.objectStorage = objectStorage;
        this.clock = clock;
    }

    @Override
    public Result<ProjectImageUpload, ApplicationError> handle(RequestProjectImageUploadCommand command) {
        var image = command.image();
        if (!projectRepository.existsById(image.projectId())) {
            return Result.failure(projectNotFound(image));
        }
        if (projectImageRepository.findByImageId(image.imageId()).isPresent()) {
            return Result.failure(alreadyRegistered(image));
        }
        var key = image.objectKey();
        var signed = objectStorage.presignUpload(key, image.contentType(), image.sizeBytes(), UPLOAD_URL_VALIDITY);
        return Result.success(new ProjectImageUpload(signed.url(), key, signed.expiresAt(), signed.headers()));
    }

    @Override
    public Result<ProjectImageRegistration, ApplicationError> handle(RegisterProjectImageCommand command) {
        var image = command.image();
        if (!projectRepository.existsById(image.projectId())) {
            return Result.failure(projectNotFound(image));
        }
        var registered = projectImageRepository.findByImageId(image.imageId());
        if (registered.isPresent()) {
            return duplicateOrConflict(image, registered.get());
        }
        if (!isUploaded(image)) {
            return Result.failure(new ApplicationError("PROJECT_IMAGE_FILE_NOT_UPLOADED",
                    "The project image is not in the file repository",
                    "no %s file of %d bytes at %s; upload it with a new URL and register it again"
                            .formatted(image.contentType(), image.sizeBytes(), image.objectKey())));
        }
        // PostgreSQL keeps microseconds: the date answered now must equal the one a re-send reads back.
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        try {
            projectImageRepository.add(image, now);
        } catch (DuplicateKeyException concurrentRegistration) {
            return duplicateOrConflict(image, projectImageRepository.findByImageId(image.imageId()).orElseThrow());
        }
        return Result.success(new ProjectImageRegistration(image, now, ProjectImageRegistration.Result.RECEIVED));
    }

    /** A re-send of the same image is answered as before (idempotent by imageId); anything else is a conflict. */
    private static Result<ProjectImageRegistration, ApplicationError> duplicateOrConflict(ProjectImage image,
                                                                                         RegisteredImage registered) {
        if (!registered.image().equals(image)) {
            return Result.failure(alreadyRegistered(image));
        }
        return Result.success(new ProjectImageRegistration(registered.image(), registered.registeredAt(),
                ProjectImageRegistration.Result.DUPLICATE));
    }

    private static ApplicationError projectNotFound(ProjectImage image) {
        return ApplicationError.notFound("project", String.valueOf(image.projectId()));
    }

    private static ApplicationError alreadyRegistered(ProjectImage image) {
        return ApplicationError.conflict("project_image",
                "image %s is already registered; its file cannot be replaced, upload it with a new imageId"
                        .formatted(image.imageId()));
    }

    /** The signature fixes type and size, but the client declares them again here, so both are checked. */
    private boolean isUploaded(ProjectImage image) {
        return objectStorage.describe(image.objectKey())
                .filter(stored -> stored.sizeBytes() == image.sizeBytes())
                .map(ObjectStorage.StoredObject::contentType)
                .filter(contentType -> contentType.strip().equalsIgnoreCase(image.contentType()))
                .isPresent();
    }
}
