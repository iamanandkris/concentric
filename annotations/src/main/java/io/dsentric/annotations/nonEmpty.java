package io.dsentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Constrains a String or collection field to be non-empty.
 *
 * <p>For strings: the value must have {@code length > 0} (after the type
 * check but before any other constraint checks).
 * <p>For collections / arrays: the value must have {@code size > 0}.
 *
 * <p>Produces a {@code ConstraintFailed("nonEmpty")} violation on failure.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface nonEmpty {
}
