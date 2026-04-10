package io.dsentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Constrains a String field to have a minimum character length.
 *
 * <p>Produces a {@code ConstraintFailed("minLength")} violation when
 * {@code value.length < min}.
 *
 * @param value  The minimum number of characters (inclusive).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface minLength {
    int value();
}
