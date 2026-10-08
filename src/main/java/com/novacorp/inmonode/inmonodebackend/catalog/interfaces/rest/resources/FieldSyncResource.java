package com.novacorp.inmonode.inmonodebackend.catalog.interfaces.rest.resources;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Offline records of the field app (US-32). Any structural error rejects the whole sync with 400, naming the field
 * and its index (e.g. {@code reservations[1].lotId}), and nothing is stored.
 */
public record FieldSyncResource(
        @NotNull @Size(max = 500) List<@NotNull @Valid ProspectRecord> prospects,
        @NotNull @Size(max = 200) List<@NotNull @Valid ReservationRecord> reservations) {

    /**
     * @param id           id generated on the device
     * @param document     DNI or foreign resident card: 8 to 12 letters or digits
     * @param registeredAt when the agent registered the prospect; the server time when omitted
     */
    public record ProspectRecord(
            @NotNull UUID id,
            @NotBlank @Pattern(regexp = "\\s*[0-9A-Za-z]{8,12}\\s*") String document,
            @NotBlank @Size(max = 150) String fullName,
            @Size(max = 20) String phone,
            Instant registeredAt) {
    }

    /**
     * @param id            id generated on the device; send the same one when retrying
     * @param prospectId    a prospect of this sync or one synchronized before
     * @param initialAmount down payment agreed in the field, in soles
     * @param reservedAt    when the agent registered the reservation on the device
     */
    public record ReservationRecord(
            @NotNull UUID id,
            @NotNull Long lotId,
            @NotNull UUID prospectId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal initialAmount,
            @NotNull Instant reservedAt) {
    }
}
