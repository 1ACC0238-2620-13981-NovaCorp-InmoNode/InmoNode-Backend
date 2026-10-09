package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.AddCoOwnerCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.CoOwner;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

public interface CoOwnerCommandService {
    Result<CoOwner, ApplicationError> handle(AddCoOwnerCommand command);
}
