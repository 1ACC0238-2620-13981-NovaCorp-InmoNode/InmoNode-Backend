package com.novacorp.inmonode.inmonodebackend.catalog.domain.model.commands;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Stores the prospects an agent registered on the device (US-04) when the app synchronizes (US-11).
 */
public record RegisterProspectsCommand(Long agentId, List<ProspectData> prospects) {

    /**
     * @param prospectId   id generated on the device
     * @param registeredAt when the agent registered it; the server time when the device did not send it
     */
    public record ProspectData(UUID prospectId, String document, String fullName, @Nullable String phone,
                               @Nullable Instant registeredAt) {
    }
}
