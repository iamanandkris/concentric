package io.concentric;

import io.concentric.annotations.*;
import io.concentric.annotations.aspectOf;
import java.util.Optional;

/**
 * Admin-level aspect for TestJvmUser — includes role which is @reserved in the source.
 *
 * Because policy annotations are NOT inherited, @reserved is not applied to role here —
 * an admin patch can freely set the role field.
 */
@aspectOf(TestJvmUser.class)
public record TestJvmAdminPatch(
    Optional<String> name,     // @nonEmpty @maxLength(50) inherited
    Optional<String> email,    // @email inherited
    Optional<String> role      // new field — not in TestJvmUser at all
) {}
