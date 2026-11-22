package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @param images the gallery of the project (US-57), in the order its images were registered; only
 *               {@code GET /api/v1/projects/{projectId}} sends it, empty when the project has no images yet
 */
public record ProjectResource(Long id, String name, String location, Double latitude, Double longitude,
                              String coverImageUrl, FinancingRulesResource financingRules, String status, java.util.List<String> stages,
                              @JsonInclude(JsonInclude.Include.NON_NULL) java.util.List<ProjectImageResource> images) {
}
