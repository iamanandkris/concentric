package io.concentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attaches one or more custom validator classes to a field.
 *
 * <p>Each class must implement {@link FieldValidator}.  Validators are
 * instantiated via their no-arg constructor and invoked in declaration order.
 * All validators are run; failures from all of them are accumulated and
 * reported together.
 *
 * <p>Keep validators synchronous; if you need external calls (e.g. uniqueness
 * checks), perform them elsewhere and return plain messages here.
 *
 * <p>Usage:
 * <pre>
 * {@literal @}validateWith({E164PhoneValidator.class, NoSpacesValidator.class})
 * String phone;
 * </pre>
 *
 * @param value  One or more {@link FieldValidator} implementation classes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface validateWith {
    Class<? extends FieldValidator<?>>[] value();
}
