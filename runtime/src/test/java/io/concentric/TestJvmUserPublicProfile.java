package io.concentric;

import io.concentric.annotations.*;

/**
 * Aspect of TestJvmUser using inherit = ALL.
 *
 * All source fields (id, name, age, email, password) must be accounted for:
 *   - id, name, email  → declared in this aspect
 *   - age, password    → explicitly excluded via aspectOf.exclude
 *
 * Adding a new field to TestJvmUser without updating this class (either
 * declaring it or adding it to exclude) will cause a startup-time error.
 */
@aspectOf(value = TestJvmUser.class,
          inherit = aspectOf.InheritMode.ALL,
          exclude = {"age", "password"})
public record TestJvmUserPublicProfile(
    Long   id,     // @immutable from source is a policy annotation — not inherited
    String name,   // @nonEmpty @maxLength(50) inherited
    String email   // @email inherited
) {}
