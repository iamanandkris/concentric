package io.dsentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attaches one or more cross-field validators to a contract class.
 *
 * <p>Each class supplied must implement
 * {@link io.dsentric.ContractValidator}{@code <T>} (where {@code T} is the
 * annotated case class) and have a public no-argument constructor so the
 * contract engine can instantiate it via reflection at derivation time.
 *
 * <p>Validators run <em>after</em> all per-field validations have passed and
 * the fully typed {@code T} has been constructed.  If any field-level
 * violation exists the contract validators are not invoked.
 *
 * <p>Violations produced by a contract validator carry an empty field path
 * and the code {@code ConstraintFailed("validateContract")}.
 *
 * <pre>{@code
 * class CheckInBeforeCheckOut implements ContractValidator<Booking> {
 *     public List<String> validate(Booking b) {
 *         if (b.checkIn().compareTo(b.checkOut()) >= 0)
 *             return List.of("checkIn must be before checkOut");
 *         return List.of();
 *     }
 * }
 *
 * @contract
 * @validateContract({CheckInBeforeCheckOut.class})
 * case class Booking(checkIn: IsoDate, checkOut: IsoDate)
 * }</pre>
 *
 * @see io.dsentric.ContractValidator
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface validateContract {
    Class<?>[] value();
}
