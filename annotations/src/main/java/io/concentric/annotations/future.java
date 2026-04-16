package io.concentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Constrains a numeric timestamp field to represent a future instant.
 *
 * <p>Applies to {@code Long} (and {@code Int}) fields whose value is a
 * Unix epoch timestamp in milliseconds.
 *
 * <p>Produces a {@code ConstraintFailed("future")} violation when
 * {@code value <= System.currentTimeMillis()}.
 *
 * <p>Maps to {@code "x-future": true} in JSON Schema.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface future {
}
