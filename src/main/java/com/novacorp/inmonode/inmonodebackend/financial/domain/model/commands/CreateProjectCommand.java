package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.GeoPoint;
import org.jspecify.annotations.Nullable;

/**
 * Registers a real estate project as a draft (US-53).
 */
public record CreateProjectCommand(String name, String location, @Nullable GeoPoint coordinates,
                                   @Nullable String coverImageUrl, FinancingRules financingRules, java.util.List<String> stages) {
    public CreateProjectCommand(String name, String location, @Nullable GeoPoint coordinates,
                                @Nullable String coverImageUrl, FinancingRules financingRules) {
        this(name, location, coordinates, coverImageUrl, financingRules, java.util.List.of("Etapa 1"));
    }
}
