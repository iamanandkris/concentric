package io.concentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Constrains a String field to match a regular expression.
 *
 * <p>The regex is evaluated using {@code java.util.regex.Pattern} full-match
 * semantics (i.e. the entire string must match, not just a substring).
 *
 * <p>Produces a {@code ConstraintFailed("pattern")} violation when the value
 * does not match.
 *
 * @param value  A valid Java regular expression.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface pattern {
    String value();
}
