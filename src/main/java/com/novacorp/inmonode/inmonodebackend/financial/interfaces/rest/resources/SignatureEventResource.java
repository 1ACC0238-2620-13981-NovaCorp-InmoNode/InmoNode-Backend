package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

/**
 * What a callback of the signature provider did (US-56).
 *
 * @param outcome SIGNED, DUPLICATE, DECLINED, REJECTED or IGNORED
 */
public record SignatureEventResource(String eventKey, String outcome) {
}
