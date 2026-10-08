package com.novacorp.inmonode.inmonodebackend.catalog.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.aggregates.Prospect;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.commands.RegisterProspectsCommand;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.commands.RegisterProspectsCommand.ProspectData;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.repositories.ProspectRepository;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.services.ProspectCommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProspectCommandServiceImpl implements ProspectCommandService {

    private final ProspectRepository prospectRepository;
    private final Clock clock;

    public ProspectCommandServiceImpl(ProspectRepository prospectRepository, Clock clock) {
        this.prospectRepository = prospectRepository;
        this.clock = clock;
    }

    /** A prospect sent twice in the same batch is stored once, with the data of its last occurrence. */
    @Override
    @Transactional
    public int handle(RegisterProspectsCommand command) {
        var known = prospectRepository.findByProspectIds(
                        command.prospects().stream().map(ProspectData::prospectId).toList())
                .stream()
                .collect(Collectors.toMap(Prospect::getProspectId, Function.identity()));
        var toSave = new LinkedHashMap<UUID, Prospect>();
        for (var data : command.prospects()) {
            var existing = known.get(data.prospectId());
            if (existing == null) {
                var registeredAt = data.registeredAt() == null ? clock.instant() : data.registeredAt();
                var prospect = Prospect.register(data.prospectId(), command.agentId(), data.document(),
                        data.fullName(), data.phone(), registeredAt);
                known.put(prospect.getProspectId(), prospect);
                toSave.put(prospect.getProspectId(), prospect);
            } else if (existing.isRegisteredBy(command.agentId())) {
                existing.updateContactInfo(data.fullName(), data.phone());
                toSave.put(existing.getProspectId(), existing);
            }
        }
        prospectRepository.saveAll(new ArrayList<>(toSave.values()));
        return toSave.size();
    }
}
