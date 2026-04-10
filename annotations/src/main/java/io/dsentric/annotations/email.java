package io.dsentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Constrains a String field to be a syntactically valid email address.
 *
 * <p>Validation uses a permissive RFC 5322 check: the value must contain
 * exactly one {@code @} character with at least one character on each side
 * and a dot in the domain part.
 *
 * <p>Produces a {@code ConstraintFailed("email")} violation on failure.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface email {
}
