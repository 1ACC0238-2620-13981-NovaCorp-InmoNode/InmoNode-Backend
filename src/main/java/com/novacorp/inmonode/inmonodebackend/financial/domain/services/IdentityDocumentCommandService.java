package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.AttachIdentityDocumentCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RequestIdentityDocumentUploadCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.IdentityDocumentRegistration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.IdentityDocumentUpload;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/**
 * The photo of the buyer's identity document that the field agent captured for a reservation (US-59).
 */
public interface IdentityDocumentCommandService {

    Result<IdentityDocumentUpload, ApplicationError> handle(RequestIdentityDocumentUploadCommand command);

    Result<IdentityDocumentRegistration, ApplicationError> handle(AttachIdentityDocumentCommand command);
}
