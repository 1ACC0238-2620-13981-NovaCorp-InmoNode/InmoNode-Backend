package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

/**
 * Special conditions authorized by management and agreed in the field for a reservation (US-58): a discount on the lot
 * price, in the lot currency; a bonus, free text that does not change the price; and notes of the negotiation. Every
 * part is optional, and blank texts are kept as absent.
 */
public record NegotiationTerms(@Nullable BigDecimal discountAmount, @Nullable String bonus, @Nullable String notes) {

    public static final int MAX_BONUS_LENGTH = 150;
    public static final int MAX_NOTES_LENGTH = 500;
    public static final NegotiationTerms NONE = new NegotiationTerms(null, null, null);

    public NegotiationTerms {
        if (discountAmount != null && (discountAmount.signum() < 0 || discountAmount.stripTrailingZeros().scale() > 2)) {
            throw new IllegalArgumentException("the discount must be zero or positive, with at most two decimals");
        }
        bonus = strippedOrNull(bonus);
        notes = strippedOrNull(notes);
        if (bonus != null && bonus.length() > MAX_BONUS_LENGTH) {
            throw new IllegalArgumentException("the bonus must have at most %d characters".formatted(MAX_BONUS_LENGTH));
        }
        if (notes != null && notes.length() > MAX_NOTES_LENGTH) {
            throw new IllegalArgumentException("the notes must have at most %d characters".formatted(MAX_NOTES_LENGTH));
        }
    }

    /** Whether the discount leaves nothing to pay for a lot of {@code lotPrice}: a valid discount is below the price. */
    public boolean discountReaches(BigDecimal lotPrice) {
        return discountAmount != null && discountAmount.compareTo(lotPrice) >= 0;
    }

    private static @Nullable String strippedOrNull(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
