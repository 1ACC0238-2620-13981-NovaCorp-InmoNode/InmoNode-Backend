package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RegisterProjectImageCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RequestProjectImageUploadCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ProjectImageCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ProjectImageFileResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.ProjectImageResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/images")
@PreAuthorize("hasRole('CATALOG_ADMIN')")
@Tag(name = "Projects", description = "Catalog of real estate projects and their lots")
public class ProjectImagesController {

    private final ProjectImageCommandService projectImageCommandService;

    public ProjectImagesController(ProjectImageCommandService projectImageCommandService) {
        this.projectImageCommandService = projectImageCommandService;
    }

    @PostMapping("/upload-url")
    @Operation(summary = "Get a presigned URL to upload a photo or render of a project (US-57)",
            description = "Back-office only (CATALOG_ADMIN), for a draft or published project. Upload the image "
                    + "with a single PUT to uploadUrl, sending the returned headers exactly as given; the URL is "
                    + "valid for 10 minutes and the storage answers 403 to any other type or size. Accepts "
                    + "image/jpeg and image/png up to 5 MB (400 otherwise). An unknown project answers 404 "
                    + "PROJECT_NOT_FOUND; an imageId already registered answers 409 PROJECT_IMAGE_CONFLICT, "
                    + "since a registered image cannot be replaced.")
    public ResponseEntity<?> requestUploadUrl(@PathVariable Long projectId,
                                              @Valid @RequestBody ProjectImageFileResource resource) {
        var image = ProjectImageResourceAssembler.toImage(projectId, resource);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                projectImageCommandService.handle(new RequestProjectImageUploadCommand(image)),
                ProjectImageResourceAssembler::toResourceFromUpload, HttpStatus.OK);
    }

    @PostMapping
    @Operation(summary = "Add an uploaded photo or render to the gallery of a project (US-57)",
            description = "Back-office only (CATALOG_ADMIN). Send the same imageId, contentType and sizeBytes "
                    + "used to ask for the upload URL. Answers 201 with result RECEIVED, or DUPLICATE when this "
                    + "image was already registered (idempotent by imageId; the original date is returned). From "
                    + "then on the image is part of the gallery in GET /api/v1/projects/{projectId}, in the order "
                    + "it was registered. Answers 400 PROJECT_IMAGE_FILE_NOT_UPLOADED when the image is not in the "
                    + "file repository or differs from the declared type or size, 404 PROJECT_NOT_FOUND for an "
                    + "unknown project, and 409 PROJECT_IMAGE_CONFLICT when the imageId is already registered "
                    + "with another file or project.")
    public ResponseEntity<?> register(@PathVariable Long projectId,
                                      @Valid @RequestBody ProjectImageFileResource resource) {
        var image = ProjectImageResourceAssembler.toImage(projectId, resource);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                projectImageCommandService.handle(new RegisterProjectImageCommand(image)),
                ProjectImageResourceAssembler::toResourceFromRegistration, HttpStatus.CREATED);
    }
}
