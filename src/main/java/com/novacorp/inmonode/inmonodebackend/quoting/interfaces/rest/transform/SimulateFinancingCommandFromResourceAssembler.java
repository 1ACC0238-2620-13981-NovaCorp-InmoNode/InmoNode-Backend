package com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.commands.SimulateFinancingCommand;
import com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest.resources.SimulateFinancingResource;

public final class SimulateFinancingCommandFromResourceAssembler {

    private SimulateFinancingCommandFromResourceAssembler() {}

    public static SimulateFinancingCommand toCommandFromResource(Long lotId, SimulateFinancingResource resource) {
        return new SimulateFinancingCommand(lotId, resource.initialPayment(), resource.termMonths());
    }
}
