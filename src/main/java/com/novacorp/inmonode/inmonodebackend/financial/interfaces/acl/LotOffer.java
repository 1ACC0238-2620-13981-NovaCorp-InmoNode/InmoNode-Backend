package com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl;

import java.math.BigDecimal;

/**
 * A lot as {@link LotAvailabilityFacade} offers it to buyers, in plain values.
 *
 * @param area                     total area, in square meters
 * @param price                    list price of the lot
 * @param available                whether a separation request could take it right now; it is decided again, under
 *                                 lock, when the lot is blocked
 * @param minDownPaymentPercentage minimum down payment of the project, as a percentage of the price (20 = 20 %)
 * @param annualInterestRate       annual interest rate of the project, as a percentage
 * @param maxTermMonths            longest financing term of the project
 */
public record LotOffer(Long lotId, Long projectId, String projectName, String lotCode, BigDecimal area,
                       BigDecimal price, String currency, boolean available, BigDecimal minDownPaymentPercentage,
                       BigDecimal annualInterestRate, int maxTermMonths) {
}
