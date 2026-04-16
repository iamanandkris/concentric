package io.concentric.annotations;

import java.util.List;

/**
 * Contract for a synchronous custom field validator.
 *
 * <p>Implement this interface to provide custom validation logic that cannot
 * be expressed with the built-in constraint annotations.  Implementations
 * must have a public no-argument constructor so the contract engine can
 * instantiate them via reflection.
 *
 * <p>Keep implementations synchronous and side-effect free. If you need to
 * call external services (database, HTTP, etc.), wrap that logic elsewhere
 * and surface only the validation messages here.
 *
 * <p>Example (Java):
 * <pre>
 * public class E164PhoneValidator implements FieldValidator&lt;String&gt; {
 *     {@literal @}Override
 *     public List&lt;String&gt; validate(String value) {
 *         if (!value.matches("\\+[1-9]\\d{1,14}"))
 *             return List.of("must be in E.164 format (e.g. +12025551234)");
 *         return List.of();
 *     }
 * }
 * </pre>
 *
 * @param <T>  The type of the field value this validator accepts.
 */
public interface FieldValidator<T> {

    /**
     * Validates {@code value} and returns a list of human-readable error
     * messages.  Return an empty list (never {@code null}) when the value is
     * valid.
     *
     * @param value  The field value to validate.  Will never be {@code null}
     *               for required fields; may be {@code null} for optional ones.
     * @return  An empty list if valid, or a non-empty list of error messages.
     */
    List<String> validate(T value);
}
