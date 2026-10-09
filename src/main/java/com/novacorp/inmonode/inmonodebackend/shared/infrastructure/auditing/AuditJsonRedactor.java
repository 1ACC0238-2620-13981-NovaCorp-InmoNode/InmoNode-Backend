package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.auditing;

import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Malformed, oversized and non-JSON bodies are omitted, never logged verbatim. */
public final class AuditJsonRedactor {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private AuditJsonRedactor() {}

    public static Object redact(byte[] body) {
        if (body.length == 0) return null;
        if (body.length > 16384) return Map.of("bodyOmitted", "size limit");
        try { return sanitize(JSON.readValue(new String(body, StandardCharsets.UTF_8), Object.class)); }
        catch (RuntimeException e) { return Map.of("bodyOmitted", "not valid JSON"); }
    }

    private static Object sanitize(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>();
            map.forEach((key, item) -> result.put(String.valueOf(key), sensitive(String.valueOf(key)) ? "****" : sanitize(item)));
            return result;
        }
        if (value instanceof List<?> list) return list.stream().map(AuditJsonRedactor::sanitize).toList();
        // Free-text error details can echo submitted secrets; log the error code and public message instead.
        return value;
    }

    private static boolean sensitive(String field) {
        var name = field.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return name.contains("password") || name.contains("token") || name.contains("secret")
                || name.contains("card") || name.equals("pan") || name.equals("cvv") || name.equals("cvc")
                || name.contains("authorization") || name.contains("credential") || name.contains("accesskey")
                || name.endsWith("url") || name.equals("headers") || name.equals("details")
                || name.equals("documentnumber") || name.equals("fullname") || name.equals("phone")
                || name.equals("objectkey");
    }

    public static String serialize(Object value) { return JSON.writeValueAsString(value); }
}
