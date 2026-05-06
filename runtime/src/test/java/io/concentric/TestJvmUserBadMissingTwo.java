package io.concentric;

import io.concentric.annotations.*;

/**
 * BAD config: inherit = ALL, no excludes, but only id/name/email declared.
 * Source fields age and password are neither declared nor excluded.
 *
 * Used in error-case tests to verify that the exception message names
 * ALL unaccounted fields, not just the first one found.
 */
@aspectOf(value = TestJvmUser.class,
          inherit = aspectOf.InheritMode.ALL)
public record TestJvmUserBadMissingTwo(
    Long   id,
    String name,
    String email
    // age and password are both unaccounted — error message must name both
) {}
