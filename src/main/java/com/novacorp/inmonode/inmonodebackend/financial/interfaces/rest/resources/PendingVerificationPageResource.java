package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.util.List;

/**
 * One page of the verification queue, in the page format of GET /vouchers (US-46).
 *
 * @param totalCount every pending evidence, also sent in the {@code Total-Count} header
 * @param page       the page returned, starting at 1
 * @param limit      the page size applied, at most 100
 */
public record PendingVerificationPageResource(List<PendingVerificationResource> items, long totalCount, int page,
                                              int limit) {
}
