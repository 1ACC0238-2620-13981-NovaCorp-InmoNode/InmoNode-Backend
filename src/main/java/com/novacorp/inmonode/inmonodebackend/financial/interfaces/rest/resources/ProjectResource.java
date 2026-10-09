package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

public record ProjectResource(Long id, String name, String location, Double latitude, Double longitude,
                              String coverImageUrl, FinancingRulesResource financingRules, String status, java.util.List<String> stages) {
}
