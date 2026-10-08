package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

/**
 * What a review of the installments did (US-24).
 *
 * @param reviewedStatements statements with something to do
 * @param overdueInstallments installments that fell overdue, with their late fee
 * @param overdueNotices     buyers told about an overdue installment
 * @param reminders          buyers reminded of an installment due soon
 */
public record InstallmentReviewSummary(int reviewedStatements, int overdueInstallments, int overdueNotices,
                                       int reminders) {

    public static final InstallmentReviewSummary NONE = new InstallmentReviewSummary(0, 0, 0, 0);

    public InstallmentReviewSummary plus(InstallmentReviewSummary other) {
        return new InstallmentReviewSummary(reviewedStatements + other.reviewedStatements,
                overdueInstallments + other.overdueInstallments, overdueNotices + other.overdueNotices,
                reminders + other.reminders);
    }
}
