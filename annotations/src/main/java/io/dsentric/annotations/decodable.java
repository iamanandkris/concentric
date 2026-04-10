package io.dsentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a single-field case class (or value class) as having its
 * {@code RawDecoder} derived automatically via {@code RawDecoder.derived[T]}.
 *
 * <p>This annotation is a documentation / tooling marker only — it carries no
 * runtime semantics.  You still need to declare the derived given explicitly:
 *
 * <pre>
 * {@literal @}decodable
 * case class Email(value: String)
 *
 * given RawDecoder[Email] = RawDecoder.derived[Email]
 *
 * // Email can now be used as a contract field type without a manual decoder:
 * {@literal @}contract
 * case class User(
 *   {@literal @}email contactEmail: Email,
 *   name: String
 * )
 * </pre>
 *
 * <p>Works with:
 * <ul>
 *   <li>Scala value classes ({@code extends AnyVal})
 *   <li>Opaque-type-style single-field wrappers
 *   <li>Any single-field case class whose inner type has a {@code RawDecoder}
 * </ul>
 *
 * <p>Multi-field case classes are not supported — use {@code @contract} and
 * {@code Contract.derived} instead.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface decodable {}
