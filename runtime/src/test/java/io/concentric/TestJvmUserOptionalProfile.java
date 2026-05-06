package io.concentric;

import io.concentric.annotations.*;
import java.util.Optional;

/**
 * Aspect of TestJvmUser using inherit = ALL with Optional fields.
 *
 * Source fields accounted for:
 *   - id, name, age, email  → declared (some as Optional)
 *   - password              → explicitly excluded
 *
 * Tests gap #7: inherit = ALL works when aspect fields are Optional<T>.
 * Absent fields should be accepted (Optional.empty()), while present values
 * still have inherited constraints applied.
 */
@aspectOf(value = TestJvmUser.class,
          inherit = aspectOf.InheritMode.ALL,
          exclude = {"password"})
public record TestJvmUserOptionalProfile(
    Long              id,       // required — @immutable is policy annotation, not inherited
    Optional<String>  name,     // optional — @nonEmpty @maxLength(50) inherited
    Optional<Integer> age,      // optional — @min(0) @max(150) inherited
    Optional<String>  email     // optional — @email inherited
) {}
