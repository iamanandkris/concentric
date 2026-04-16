package io.concentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Constrains a String field to be a valid URL.
 *
 * <p>Applies to {@code String} fields.
 *
 * <p>Produces a {@code ConstraintFailed("url")} violation when the value
 * is not a valid HTTP or HTTPS URL.
 *
 * <p>Maps to {@code "format": "uri"} in JSON Schema.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface url {
}
