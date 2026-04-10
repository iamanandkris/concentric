package io.dsentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Flattens all fields of a nested contract type into the parent's wire format.
 *
 * <p>Without {@code @include} a nested case-class field is stored as a
 * sub-document under the field's key.  With {@code @include} the inner
 * type's fields are promoted to the same level as the parent's own fields
 * in the wire representation, while the Scala model retains a proper nested
 * type for type-safety.
 *
 * <pre>
 * {@literal @}contract
 * case class Timestamps(createdAt: Long, updatedAt: Long)
 *
 * {@literal @}contract
 * case class User(
 *   id:   Long,
 *   name: String,
 *   {@literal @}include timestamps: Timestamps
 * )
 *
 * // Wire format (flat):
 * // {"id": 1, "name": "Alice", "createdAt": 1700000000, "updatedAt": 1700001000}
 *
 * // NOT nested:
 * // {"id": 1, "name": "Alice", "timestamps": {"createdAt": ..., "updatedAt": ...}}
 * </pre>
 *
 * <p>Constraints, immutability, and other annotations on the inner type's
 * fields are fully respected.  {@code Contract.toRaw} serialises back to the
 * same flat wire format.
 *
 * <p>Only one level of {@code @include} nesting is supported; recursive
 * flattening is not performed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface include {}
