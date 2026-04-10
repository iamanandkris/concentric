package io.dsentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as internal — it will be stripped from the sanitized output
 * of {@code Contract.sanitize}.
 *
 * <p>Typical use-cases: audit timestamps, internal identifiers, server-side
 * computed fields that must never be exposed in API responses.
 *
 * <p>Usage:
 * <pre>
 * case class User(
 *   {@literal @}immutable {@literal @}internal id: Long,
 *   name: String
 * )
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface internal {
}
