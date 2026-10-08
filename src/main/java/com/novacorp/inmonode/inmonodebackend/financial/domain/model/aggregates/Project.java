package com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.GeoPoint;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectStatus;
import org.jspecify.annotations.Nullable;

/**
 * Real estate project of the catalog (US-53): a set of lots sold under the same financing rules.
 *
 * <p>Its lots are a separate aggregate that references the project by id, because they are loaded in
 * bulk from the plan and change state on their own (blocks, reservations, sales).</p>
 */
public class Project {

    private final @Nullable Long id;
    private final String name;
    private final String location;
    private final @Nullable GeoPoint coordinates;
    private final @Nullable String coverImageUrl;
    private final FinancingRules financingRules;
    private ProjectStatus status;

    private Project(@Nullable Long id, String name, String location, @Nullable GeoPoint coordinates,
                    @Nullable String coverImageUrl, FinancingRules financingRules, ProjectStatus status) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.coordinates = coordinates;
        this.coverImageUrl = coverImageUrl;
        this.financingRules = financingRules;
        this.status = status;
    }

    /** US-53, Scenario 1: a new project starts as a draft. */
    public static Project create(String name, String location, @Nullable GeoPoint coordinates,
                                 @Nullable String coverImageUrl, FinancingRules financingRules) {
        if (financingRules == null) {
            throw new IllegalArgumentException("financingRules is required");
        }
        return new Project(null, requireText(name, "name"), requireText(location, "location"), coordinates,
                blankToNull(coverImageUrl), financingRules, ProjectStatus.DRAFT);
    }

    /** Rebuilds an already persisted project. */
    public static Project restore(Long id, String name, String location, @Nullable GeoPoint coordinates,
                                  @Nullable String coverImageUrl, FinancingRules financingRules,
                                  ProjectStatus status) {
        return new Project(id, name, location, coordinates, coverImageUrl, financingRules, status);
    }

    public boolean isPublished() {
        return status == ProjectStatus.PUBLISHED;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public @Nullable Long getId() { return id; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public @Nullable GeoPoint getCoordinates() { return coordinates; }
    public @Nullable String getCoverImageUrl() { return coverImageUrl; }
    public FinancingRules getFinancingRules() { return financingRules; }
    public ProjectStatus getStatus() { return status; }
}
