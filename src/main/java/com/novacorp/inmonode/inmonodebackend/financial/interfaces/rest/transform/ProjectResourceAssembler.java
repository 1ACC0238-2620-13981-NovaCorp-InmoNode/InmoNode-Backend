package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectDetail;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectDetail.GalleryImage;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.FinancingRulesResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ProjectImageResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ProjectResource;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class ProjectResourceAssembler {

    private ProjectResourceAssembler() {}

    /** The project without its gallery, as the back office's writes answer it. */
    public static ProjectResource toResourceFromEntity(Project project) {
        return toResource(project, null);
    }

    /** The project with its gallery, as its page shows it (US-57). */
    public static ProjectResource toResourceFromDetail(ProjectDetail detail) {
        return toResource(detail.project(),
                detail.gallery().stream().map(ProjectResourceAssembler::toImageResource).toList());
    }

    private static ProjectResource toResource(Project project, @Nullable List<ProjectImageResource> images) {
        var coordinates = project.getCoordinates();
        var rules = project.getFinancingRules();
        return new ProjectResource(project.getId(), project.getName(), project.getLocation(),
                coordinates == null ? null : coordinates.latitude(),
                coordinates == null ? null : coordinates.longitude(),
                project.getCoverImageUrl(),
                new FinancingRulesResource(rules.minDownPaymentPercentage(), rules.annualInterestRate(),
                        rules.maxTermMonths(), rules.lateFeeRate()),
                project.getStatus().name(), project.getStages(), images);
    }

    private static ProjectImageResource toImageResource(GalleryImage galleryImage) {
        var image = galleryImage.image();
        return new ProjectImageResource(image.imageId(), image.contentType(), galleryImage.url().toString(),
                galleryImage.expiresAt());
    }
}
