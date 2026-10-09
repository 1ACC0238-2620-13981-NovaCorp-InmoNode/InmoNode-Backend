package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.CreateProjectCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.PublishProjectCommand;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/**
 * Command side of the project catalog.
 */
public interface ProjectCommandService {

    /** US-53, Scenario 1: registers the project as a draft. */
    Result<Project, ApplicationError> handle(CreateProjectCommand command);

    /** US-53, Scenario 1: publishes the project; fails while it has no lots. Idempotent. */
    Result<Project, ApplicationError> handle(PublishProjectCommand command);
}
