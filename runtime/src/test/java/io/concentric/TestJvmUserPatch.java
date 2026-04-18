package io.concentric;

import io.concentric.annotations.*;
import io.concentric.annotations.aspectOf;
import java.util.Optional;

/**
 * Aspect record for TestJvmUser — used in JvmAspectSpec.
 *
 * Declared fields:
 *   name  → @nonEmpty @maxLength(50) inherited from TestJvmUser.name
 *   email → @email inherited from TestJvmUser.email
 *
 * Excluded from source:
 *   id       (@immutable — @immutable is not inherited anyway; excluded by omission)
 *   age      (excluded by omission)
 *   password (@masked — not inherited anyway; excluded by omission)
 */
@aspectOf(TestJvmUser.class)
public record TestJvmUserPatch(
    Optional<String> name,
    Optional<String> email
) {}
