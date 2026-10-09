package com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.resources.HealthResource;

import java.util.Map;

public class HealthResourceAssembler {

    public static HealthResource toResourceFromDatabaseAvailability(boolean databaseAvailable) {
        var status = databaseAvailable ? "UP" : "DOWN";
        return new HealthResource(status, Map.of("api", "UP", "database", status),
                databaseAvailable ? "All services are available" : "The main database is unavailable");
    }
}
