package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

/**
 * Life cycle of a contract. It is stored once the back office issues it; until then the buyer sees it as in
 * preparation (US-21, Scenario 2). It is then sent to the signature provider (US-56).
 */
public enum ContractStatus {
    /** Issued; its request to sign is on its way to the provider, or was never made (contracts issued before US-56). */
    ISSUED,
    /** The provider has it: the buyer signs it with the signing link. */
    SIGNATURE_REQUESTED,
    /** The provider confirmed the signature: the signed document is kept and the lot is sold. */
    SIGNED,
    /** The buyer declined to sign; the back office can send it again. */
    SIGNATURE_DECLINED
}
