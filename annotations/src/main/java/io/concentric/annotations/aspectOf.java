package io.concentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java record, Kotlin data class, or POJO as a structural aspect of
 * another contract class.
 *
 * <p>An aspect is a partial or variant view of a source contract — typically
 * used for PATCH payloads, registration forms, or admin overrides.  The
 * concentric runtime uses this annotation to:
 *
 * <ul>
 *   <li>Inherit <em>constraint</em> annotations ({@code @nonEmpty}, {@code @email},
 *       {@code @min}, etc.) from the corresponding field in the source class.</li>
 *   <li>Leave <em>policy</em> annotations ({@code @immutable}, {@code @internal},
 *       {@code @reserved}, {@code @masked}) out of the inherited set — the
 *       aspect author controls access rules independently.</li>
 * </ul>
 *
 * <p>Fields declared in the aspect that have no counterpart in the source class
 * are accepted as-is; only the annotations directly placed on those fields apply.
 *
 * <p>Aspects are always <em>closed</em>: unknown input keys are rejected regardless
 * of whether the source contract is open or closed.
 *
 * <p>Example (Java record PATCH):
 * <pre>
 * {@literal @}contract
 * public record User(
 *     {@literal @}immutable            Long   id,
 *     {@literal @}nonEmpty             String name,
 *     {@literal @}email                String email
 * ) {}
 *
 * {@literal @}aspectOf(User.class)
 * public record UserPatch(
 *     Optional{@literal <}String{@literal >} name,   // {@literal @}nonEmpty inherited
 *     Optional{@literal <}String{@literal >} email   // {@literal @}email inherited
 * ) {}
 *
 * JvmContract{@literal <}User{@literal >}      userContract  = JvmContract.ofRecord(User.class);
 * JvmContract{@literal <}UserPatch{@literal >} patchContract = JvmContract.ofAspect(UserPatch.class, User.class);
 *
 * // Validate a PATCH request:
 * var raw = Map.of("email", "new{@literal @}example.com");
 * ValidationResult{@literal <}UserPatch{@literal >} result = patchContract.validate(raw);
 *
 * // Apply the patch to the stored object:
 * if (result.isValid()) {
 *     userContract.validatePatch(currentRaw, result.getValue().get(), patchContract);
 * }
 * </pre>
 *
 * <p>Example (Kotlin data class PATCH):
 * <pre>
 * {@literal @}aspectOf(User::class.java)
 * data class UserPatch(
 *     val name:  Optional{@literal <}String{@literal >} = Optional.empty(),  // {@literal @}nonEmpty inherited
 *     val email: Optional{@literal <}String{@literal >} = Optional.empty()   // {@literal @}email inherited
 * )
 *
 * val patchContract = JvmContract.ofAspect(UserPatch::class.java, User::class.java)
 * </pre>
 *
 * @see io.concentric.JvmContract#ofAspect(Class, Class)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface aspectOf {
    /**
     * The source contract class that this aspect is a structural variant of.
     */
    Class<?> value();
}
