package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.util.List;

public record ContractToIssuePageResource(List<ContractToIssueResource> items, long totalCount, int page, int limit) {
}
