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
 * <h2>Inheritance modes</h2>
 *
 * <p>The {@link #inherit()} attribute controls whether exhaustiveness checking is
 * applied to source fields:
 *
 * <ul>
 *   <li>{@link InheritMode#EXPLICIT} (default) — opt-in: you declare exactly the
 *       fields you want.  Source fields not mentioned are silently absent from the
 *       aspect.  No validation that all source fields were considered.</li>
 *   <li>{@link InheritMode#ALL} — exhaustive: every source field must either be
 *       declared in the aspect constructor OR listed in {@link #exclude()}.  If a
 *       new field is added to the source contract, the aspect fails at startup
 *       until you decide whether to include or exclude it.  This prevents silent
 *       schema drift.</li>
 * </ul>
 *
 * <p>The constructor is <em>not</em> affected by {@code inherit = ALL}: the aspect
 * class still declares exactly the fields it wants.  {@code inherit = ALL} only
 * adds a validation check that no source field was silently forgotten.
 *
 * <h2>Example — EXPLICIT mode (default)</h2>
 * <pre>
 * {@literal @}contract
 * public record User(
 *     {@literal @}immutable            Long   id,
 *     {@literal @}nonEmpty             String name,
 *     {@literal @}email                String email,
 *                                      int    age
 * ) {}
 *
 * {@literal @}aspectOf(User.class)
 * public record UserPatch(
 *     Optional{@literal <}String{@literal >} name,   // {@literal @}nonEmpty inherited
 *     Optional{@literal <}String{@literal >} email   // {@literal @}email inherited
 *     // id and age silently absent — no error
 * ) {}
 * </pre>
 *
 * <h2>Example — inherit = ALL mode</h2>
 * <pre>
 * {@literal @}aspectOf(value = User.class,
 *           inherit = aspectOf.InheritMode.ALL,
 *           exclude = {"age"})        // age intentionally omitted
 * public record UserPublicProfile(
 *     Long   id,    // {@literal @}immutable inherited (policy — but declared here)
 *     String name,  // {@literal @}nonEmpty inherited
 *     String email  // {@literal @}email inherited
 *     // age excluded via exclude list — startup validation passes
 *     // adding a new field to User without updating here causes a startup error
 * ) {}
 * </pre>
 *
 * @see io.concentric.JvmContract#ofAspect(Class, Class)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface aspectOf {

    /**
     * Controls whether exhaustiveness checking is applied to source fields.
     */
    enum InheritMode {
        /**
         * Default: opt-in field selection.  Source fields not mentioned in
         * the aspect are silently absent — no exhaustiveness check.
         */
        EXPLICIT,
        /**
         * Exhaustive mode: every source field must be either declared in the
         * aspect constructor or listed in {@link aspectOf#exclude()}.  Startup
         * fails if any source field is silently forgotten.
         */
        ALL
    }

    /**
     * The source contract class that this aspect is a structural variant of.
     */
    Class<?> value();

    /**
     * Inheritance mode.  Use {@link InheritMode#ALL} to enable exhaustiveness
     * checking.  Defaults to {@link InheritMode#EXPLICIT}.
     */
    InheritMode inherit() default InheritMode.EXPLICIT;

    /**
     * Source field names to explicitly exclude when {@link #inherit()} is
     * {@link InheritMode#ALL}.  Each name must exist in the source contract;
     * specifying a non-existent name is an error.  This attribute is only
     * valid when {@code inherit = InheritMode.ALL}; specifying it with
     * {@code EXPLICIT} mode causes a startup error.
     */
    String[] exclude() default {};
}
