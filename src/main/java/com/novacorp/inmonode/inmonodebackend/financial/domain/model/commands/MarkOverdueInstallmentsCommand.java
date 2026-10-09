package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;
import java.time.LocalDate;
public record MarkOverdueInstallmentsCommand(LocalDate asOfDate) {}
