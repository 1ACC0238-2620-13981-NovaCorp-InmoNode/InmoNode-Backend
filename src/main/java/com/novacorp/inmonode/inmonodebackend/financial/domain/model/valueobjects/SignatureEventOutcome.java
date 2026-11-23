package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

/** What a callback of the signature provider did (US-56). */
public enum SignatureEventOutcome {
    /** It registered the signature of a contract and sold its lot. */
    SIGNED,
    /** The contract was already signed: nothing changed (Scenario 2). */
    DUPLICATE,
    /** The buyer declined to sign. */
    DECLINED,
    /** The provider's API did not confirm what the callback said. */
    REJECTED,
    /** Not about a contract of InmoNode, or a type that changes nothing. */
    IGNORED
}
