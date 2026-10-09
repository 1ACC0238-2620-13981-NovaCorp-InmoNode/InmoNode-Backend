package com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.GeoPoint;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ProjectTest {

    private static final FinancingRules RULES =
            new FinancingRules(new BigDecimal("10"), new BigDecimal("12"), 120, new BigDecimal("2"));

    @Test
    void newProjectStartsAsADraft() {
        var project = Project.create("  Los Pinos  ", " Chilca, Lima ", new GeoPoint(-12.5, -76.7), " ", RULES);

        assertEquals(ProjectStatus.DRAFT, project.getStatus());
        assertFalse(project.isPublished());
        assertNull(project.getId());
        assertEquals("Los Pinos", project.getName());
        assertEquals("Chilca, Lima", project.getLocation());
        assertNull(project.getCoverImageUrl());
        assertEquals(RULES, project.getFinancingRules());
    }

    @Test
    void projectWithoutLotsCannotBePublished() {
        var project = Project.create("Los Pinos", "Chilca", null, null, RULES);

        assertFalse(project.publish(0));
        assertEquals(ProjectStatus.DRAFT, project.getStatus());
    }

    @Test
    void projectWithLotsIsPublishedAndPublishingAgainChangesNothing() {
        var project = Project.create("Los Pinos", "Chilca", null, null, RULES);

        assertTrue(project.publish(3));
        assertTrue(project.isPublished());
        assertTrue(project.publish(3));
        assertEquals(ProjectStatus.PUBLISHED, project.getStatus());
    }

    @Test
    void nameLocationAndFinancingRulesAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> Project.create(" ", "Chilca", null, null, RULES));
        assertThrows(IllegalArgumentException.class, () -> Project.create("Los Pinos", "", null, null, RULES));
        assertThrows(IllegalArgumentException.class, () -> Project.create("Los Pinos", "Chilca", null, null, null));
    }
}
