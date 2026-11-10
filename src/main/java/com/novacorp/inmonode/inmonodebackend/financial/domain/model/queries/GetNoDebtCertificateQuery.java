package com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries;

/**
 * The no-debt certificate of one of the calling buyer's account statements (US-26).
 */
public record GetNoDebtCertificateQuery(Long accountStatementId) {
}
