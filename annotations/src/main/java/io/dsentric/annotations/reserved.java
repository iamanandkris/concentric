package io.dsentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as reserved — it cannot be set by API callers at all.
 *
 * <p>Unlike {@link immutable} (which allows the field to be set on creation),
 * a reserved field is always managed server-side.  Any attempt to supply a
 * value for it — whether in a create or patch operation — will be rejected
 * with a {@code ReservedField} violation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface reserved {
}
