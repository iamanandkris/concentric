package io.concentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures a tagged-union / discriminated-union wire format for an
 * {@code Either[A, B]} field.
 *
 * <p>Without this annotation an {@code Either} field uses the envelope
 * format {@code {"left": value}} / {@code {"right": value}}.  With
 * {@code @discriminator} the field value is a flat map that contains a
 * discriminator key whose value identifies the active branch:
 *
 * <pre>
 * {@literal @}contract
 * case class PetHolder(
 *   {@literal @}discriminator("type", left = "cat", right = "dog")
 *   pet: Either[Cat, Dog]
 * )
 *
 * // Wire format for Left(Cat("Whiskers")):
 * // {"type": "cat", "name": "Whiskers"}
 *
 * // Wire format for Right(Dog("Rex")):
 * // {"type": "dog", "name": "Rex"}
 * </pre>
 *
 * <p>The discriminator key is stripped before the inner {@code RawDecoder}
 * is called, so the inner type's own fields do not include it.
 *
 * @param value  The key name used as the discriminator (e.g. {@code "type"}).
 * @param left   The discriminator value that selects the Left branch.
 * @param right  The discriminator value that selects the Right branch.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface discriminator {
    /** The key name used as the discriminator in the wire map (e.g. {@code "type"}). */
    String value();
    /** The string value of the discriminator key that maps to the Left branch. */
    String left();
    /** The string value of the discriminator key that maps to the Right branch. */
    String right();
}
