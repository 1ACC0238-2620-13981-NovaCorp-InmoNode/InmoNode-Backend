package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

/**
 * Life cycle of a contract. It is stored once the back office issues it; until then the buyer sees it as in
 * preparation (US-21, Scenario 2). The signature (US-30) will add the next states.
 */
public enum ContractStatus {
    ISSUED
}
