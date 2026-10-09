package com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects;

/**
 * Actor roles of the platform, one per actor. Each protected endpoint declares which roles it admits.
 */
public enum Role {
    BUYER,
    FIELD_AGENT,
    CATALOG_ADMIN,
    FINANCE_ADMIN
}
