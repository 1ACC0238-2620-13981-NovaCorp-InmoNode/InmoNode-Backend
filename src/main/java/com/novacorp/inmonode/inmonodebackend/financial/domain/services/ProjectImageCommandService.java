package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RegisterProjectImageCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RequestProjectImageUploadCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectImageRegistration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectImageUpload;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/**
 * The photos and renders the back office uploads for the gallery of a project (US-57).
 */
public interface ProjectImageCommandService {

    Result<ProjectImageUpload, ApplicationError> handle(RequestProjectImageUploadCommand command);

    Result<ProjectImageRegistration, ApplicationError> handle(RegisterProjectImageCommand command);
}
