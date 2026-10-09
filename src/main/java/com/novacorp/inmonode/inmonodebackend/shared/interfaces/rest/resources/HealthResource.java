package com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.resources;

import java.util.Map;

/** Public status of the API and its main database, consumed by load balancers (US-34). */
public record HealthResource(String status, Map<String, String> services, String message) {
}
