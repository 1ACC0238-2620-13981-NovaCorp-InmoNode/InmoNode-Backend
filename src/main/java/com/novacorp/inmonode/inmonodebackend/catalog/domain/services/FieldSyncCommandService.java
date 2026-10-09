package com.novacorp.inmonode.inmonodebackend.catalog.domain.services;

import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.commands.SyncFieldRecordsCommand;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.valueobjects.FieldSyncResult;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/**
 * Receives the offline records of the field app.
 */
public interface FieldSyncCommandService {

    /**
     * US-11, US-12, US-32: stores the prospects and has each reservation consolidated against the central
     * availability, one by one, so accepted reservations are kept even when others conflict.
     *
     * @return a validation error, before storing anything, when a reservation refers to an unknown prospect
     */
    Result<FieldSyncResult, ApplicationError> handle(SyncFieldRecordsCommand command);
}
