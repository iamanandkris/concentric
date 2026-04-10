package io.dsentric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Dual-purpose regex annotation:
 *
 * <h3>On a {@code @contract} field</h3>
 * <p>Constrains the field value to fully match the supplied regular expression.
 * Behaves like {@code @pattern} but signals intent to extract structure rather
 * than merely validate format.  Produces a {@code ConstraintFailed("extract")}
 * violation when the string does not match.
 *
 * <pre>{@code
 * @contract
 * case class Event(
 *   @extract("\\d{4}-\\d{2}-\\d{2}") date: String
 * )
 * }</pre>
 *
 * <h3>On a {@code @decodable} case class (with {@code RawDecoder.derived})</h3>
 * <p>Turns the case class into a structured string parser.  The macro
 * compiles the regex at derivation time, counts its capture groups, and
 * generates a {@link io.dsentric.RawDecoder} that:
 * <ol>
 *   <li>Accepts a raw {@code String} input.</li>
 *   <li>Fully matches it against the regex ({@code Matcher.matches()}).</li>
 *   <li>If the regex has <em>N</em> capture groups: decodes group 1..N
 *       into the corresponding constructor fields using their individual
 *       {@link io.dsentric.RawDecoder} instances.</li>
 *   <li>If the regex has <em>0</em> capture groups (single-field class only):
 *       validates the format and wraps the original string.</li>
 * </ol>
 *
 * <pre>{@code
 * // Three-group extraction
 * @decodable
 * @extract("(\\d{4})-(\\d{2})-(\\d{2})")
 * case class IsoDate(year: Int, month: Int, day: Int)
 * given RawDecoder[IsoDate] = RawDecoder.derived[IsoDate]
 *
 * // Validate-only (0 groups, 1 field)
 * @decodable
 * @extract("[A-Z]{2}-\\d{4}")
 * case class ProductCode(value: String)
 * given RawDecoder[ProductCode] = RawDecoder.derived[ProductCode]
 * }</pre>
 *
 * <p>A compile error is raised when the number of capture groups does not
 * match the number of constructor fields (for the N-group case), or when
 * the regex itself is syntactically invalid.
 *
 * @param value  A valid Java regular expression.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE})
public @interface extract {
    String value();
}
