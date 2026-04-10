package io.dsentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as immutable — it may be set on creation but cannot be
 * changed once the contract has accepted the object.
 *
 * <p>Any attempt to include an immutable field in a patch / partial-update
 * will be rejected with an {@code ImmutableField} violation.
 *
 * <p>Usage:
 * <pre>
 * // Scala
 * case class User(@immutable id: Long, name: String)
 *
 * // Kotlin
 * data class User(@immutable val id: Long, val name: String)
 *
 * // Java
 * public record User(@immutable Long id, String name) {}
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface immutable {
}
