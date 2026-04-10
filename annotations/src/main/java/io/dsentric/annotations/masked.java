package io.dsentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Replaces the field value with a fixed mask string in the sanitized output
 * of {@code Contract.sanitize}.
 *
 * <p>The field value is stored and validated normally; only the sanitized
 * (outbound) representation is replaced.  Useful for passwords, tokens, PII.
 *
 * <p>Usage:
 * <pre>
 * // Replace with default "***"
 * {@literal @}masked
 * String password;
 *
 * // Replace with a custom mask
 * {@literal @}masked("&lt;redacted&gt;")
 * String ssn;
 * </pre>
 *
 * @param value  The string that replaces the real value in sanitized output.
 *               Defaults to {@code "***"}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface masked {
    String value() default "***";
}
