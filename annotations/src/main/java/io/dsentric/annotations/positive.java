package io.dsentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Constrains a numeric field to be strictly positive (greater than zero).
 *
 * <p>Applies to {@code Int}, {@code Long}, {@code Float}, {@code Double},
 * and {@code BigDecimal} fields.
 *
 * <p>Produces a {@code ConstraintFailed("positive")} violation when
 * {@code value <= 0}.
 *
 * <p>Maps to {@code "exclusiveMinimum": 0} in JSON Schema (draft-07).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface positive {
}
