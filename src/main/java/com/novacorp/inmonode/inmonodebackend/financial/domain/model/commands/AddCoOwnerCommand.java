package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.CoOwner;
import java.util.UUID;

public record AddCoOwnerCommand(UUID transactionId, CoOwner coOwner) {}
