package io.concentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Constrains a String field to have a maximum character length.
 *
 * <p>Produces a {@code ConstraintFailed("maxLength")} violation when
 * {@code value.length > max}.
 *
 * @param value  The maximum number of characters (inclusive).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface maxLength {
    int value();
}
