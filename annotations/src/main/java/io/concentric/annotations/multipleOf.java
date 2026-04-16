package io.concentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Constrains a numeric field to be an exact multiple of the given divisor.
 *
 * <p>Applies to {@code Int}, {@code Long}, {@code Float}, {@code Double},
 * and {@code BigDecimal} fields.
 *
 * <p>Produces a {@code ConstraintFailed("multipleOf")} violation when
 * {@code value % divisor != 0} (checked with floating-point arithmetic).
 *
 * @param value  The divisor; must be a positive non-zero number.
 *
 * <p>Maps to {@code "multipleOf": value} in JSON Schema.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface multipleOf {
    double value();
}
