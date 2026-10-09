package com.novacorp.inmonode.inmonodebackend.catalog.domain.model.aggregates;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A potential buyer a field agent met on site (US-04). It is identified by the id generated on the device, so the
 * same prospect re-sent by a later synchronization is recognized.
 */
public class Prospect {

    public static final int MAX_FULL_NAME_LENGTH = 150;

    /** DNI (8 digits) or foreign resident card (up to 12 letters and digits). */
    private static final Pattern DOCUMENT = Pattern.compile("[0-9A-Z]{8,12}");
    private static final Pattern PHONE = Pattern.compile("\\+?[0-9 -]{6,20}");

    private final @Nullable Long id;
    private final UUID prospectId;
    private final Long agentId;
    private final String document;
    private String fullName;
    private @Nullable String phone;
    private final Instant registeredAt;

    private Prospect(@Nullable Long id, UUID prospectId, Long agentId, String document, String fullName,
                     @Nullable String phone, Instant registeredAt) {
        this.id = id;
        this.prospectId = prospectId;
        this.agentId = agentId;
        this.document = document;
        this.fullName = fullName;
        this.phone = phone;
        this.registeredAt = registeredAt;
    }

    /**
     * US-04: the identity document is mandatory (Scenario 2), as is the name; the phone is optional.
     *
     * @param prospectId   id generated on the device
     * @param registeredAt when the agent registered the prospect
     */
    public static Prospect register(UUID prospectId, Long agentId, String document, String fullName,
                                    @Nullable String phone, Instant registeredAt) {
        if (prospectId == null || agentId == null || registeredAt == null) {
            throw new IllegalArgumentException("a prospect needs its id, agent and registration date");
        }
        return new Prospect(null, prospectId, agentId, normalizeDocument(document), requireFullName(fullName),
                normalizePhone(phone), registeredAt);
    }

    /** Rebuilds an already persisted prospect. */
    public static Prospect restore(Long id, UUID prospectId, Long agentId, String document, String fullName,
                                   @Nullable String phone, Instant registeredAt) {
        return new Prospect(id, prospectId, agentId, document, fullName, phone, registeredAt);
    }

    /** The agent corrected the name or phone on the device; the document identifies the person and stays. */
    public void updateContactInfo(String fullName, @Nullable String phone) {
        this.fullName = requireFullName(fullName);
        this.phone = normalizePhone(phone);
    }

    public boolean isRegisteredBy(Long agentId) {
        return this.agentId.equals(agentId);
    }

    private static String normalizeDocument(String document) {
        if (document == null || document.isBlank()) {
            throw new IllegalArgumentException("the identity document is required");
        }
        var normalized = document.trim().toUpperCase(Locale.ROOT);
        if (!DOCUMENT.matcher(normalized).matches()) {
            throw new IllegalArgumentException("the identity document must have 8 to 12 letters or digits");
        }
        return normalized;
    }

    private static String requireFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("the full name is required");
        }
        var trimmed = fullName.trim();
        if (trimmed.length() > MAX_FULL_NAME_LENGTH) {
            throw new IllegalArgumentException("the full name must have at most %d characters".formatted(MAX_FULL_NAME_LENGTH));
        }
        return trimmed;
    }

    private static @Nullable String normalizePhone(@Nullable String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        var trimmed = phone.trim();
        if (!PHONE.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("the phone must have 6 to 20 digits, spaces or dashes");
        }
        return trimmed;
    }

    public @Nullable Long getId() { return id; }
    public UUID getProspectId() { return prospectId; }
    public Long getAgentId() { return agentId; }
    public String getDocument() { return document; }
    public String getFullName() { return fullName; }
    public @Nullable String getPhone() { return phone; }
    public Instant getRegisteredAt() { return registeredAt; }
}
