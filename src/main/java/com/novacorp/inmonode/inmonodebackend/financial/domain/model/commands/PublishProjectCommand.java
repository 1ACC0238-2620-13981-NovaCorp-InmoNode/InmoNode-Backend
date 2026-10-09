package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

/**
 * Makes a draft project visible in the catalog (US-53).
 */
public record PublishProjectCommand(Long projectId) {
}
