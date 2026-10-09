package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

/** Legal participant designated before contract issuance (US-28). */
public record CoOwner(String fullName, String documentType, String documentNumber) {
    public CoOwner {
        if (fullName == null || fullName.isBlank() || fullName.strip().length() > 200) {
            throw new IllegalArgumentException("fullName is required and must have at most 200 characters");
        }
        fullName = fullName.strip();
        if (documentType == null || documentNumber == null) {
            throw new IllegalArgumentException("the co-owner needs an identity document");
        }
        documentType = documentType.strip().toUpperCase(java.util.Locale.ROOT);
        documentNumber = documentNumber.strip().toUpperCase(java.util.Locale.ROOT);
        var valid = switch (documentType) {
            case "DNI" -> documentNumber.matches("[0-9]{8}");
            case "RUC" -> documentNumber.matches("[0-9]{11}");
            case "CE", "PASSPORT" -> documentNumber.matches("[A-Z0-9]{6,20}");
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("invalid co-owner document: use DNI, RUC, CE or PASSPORT");
    }
}
