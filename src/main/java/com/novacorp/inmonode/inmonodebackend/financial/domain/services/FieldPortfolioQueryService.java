package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetFieldPortfolioQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PortfolioProject;

import java.util.List;

/**
 * Query side of the catalog download for the field app.
 */
public interface FieldPortfolioQueryService {

    /** US-02: published projects ordered by name, each with its lots ordered by code. */
    List<PortfolioProject> handle(GetFieldPortfolioQuery query);
}
