package io.dsentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Constrains a String field to be a valid UUID (version 1–5).
 *
 * <p>Applies to {@code String} fields.
 *
 * <p>Produces a {@code ConstraintFailed("uuid")} violation when the value
 * does not match the canonical UUID format
 * {@code xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx}.
 *
 * <p>Maps to {@code "format": "uuid"} in JSON Schema.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface uuid {
}
