package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.documentation.openapi.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public operation whose answer can depend on the caller's token, such as a catalog read that also returns
 * drafts to CATALOG_ADMIN. The OpenAPI document then lists the operation as callable without security or with
 * {@code bearerAuth}, so clients may omit the token while Swagger UI still sends it once authorized.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OptionalAuthentication {
}
