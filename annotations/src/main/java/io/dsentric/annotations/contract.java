package io.dsentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class (case class / data class / record / POJO) as a dsentric contract.
 *
 * <p>Usage:
 * <pre>
 * // Scala
 * {@literal @}contract
 * case class User(id: Long, name: String)
 *
 * // Kotlin
 * {@literal @}contract
 * data class User(val id: Long, val name: String)
 *
 * // Java
 * {@literal @}contract
 * public record User(Long id, String name) {}
 * </pre>
 *
 * @param open  Legacy open-contract flag. When {@code true} the contract
 *              accepts additional properties not declared in the class
 *              definition. Defaults to {@code false} (closed / strict
 *              contract).
 *
 *              <p>Prefer deriving {@code OpenContract} / {@code JvmOpenContract}
 *              instead of relying on this flag. It remains available for
 *              compatibility with existing callers.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface contract {
    @Deprecated(since = "0.2.0", forRemoval = false)
    boolean open() default false;
}
