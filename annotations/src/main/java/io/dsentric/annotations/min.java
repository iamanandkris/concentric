package io.dsentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Constrains a numeric field to a minimum value (inclusive).
 *
 * <p>Applies to {@code Int}, {@code Long}, {@code Float}, {@code Double},
 * and {@code BigDecimal} fields.
 *
 * <p>Produces a {@code ConstraintFailed("min")} violation when
 * {@code value < min}.
 *
 * @param value  The minimum numeric value (inclusive), expressed as a long.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface min {
    long value();
}
