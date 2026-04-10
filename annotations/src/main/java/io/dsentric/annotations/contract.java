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
 * @param open  When {@code true} the contract accepts additional properties not
 *              declared in the class definition.  Defaults to {@code false}
 *              (closed / strict contract).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface contract {
    boolean open() default false;
}
