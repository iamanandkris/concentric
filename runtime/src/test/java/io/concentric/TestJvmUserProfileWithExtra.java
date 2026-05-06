package io.concentric;

import io.concentric.annotations.*;

/**
 * Aspect of TestJvmUser using inherit = ALL, with an extra field not in source.
 *
 * Source fields accounted for:
 *   - name, email      → declared in this aspect
 *   - id, age, password → explicitly excluded via aspectOf.exclude
 *
 * Extra field:
 *   - displayName      → new field, not in source (own annotations only)
 */
@aspectOf(value = TestJvmUser.class,
          inherit = aspectOf.InheritMode.ALL,
          exclude = {"id", "age", "password"})
public record TestJvmUserProfileWithExtra(
    String name,          // @nonEmpty @maxLength(50) inherited
    String email,         // @email inherited
    @nonEmpty String displayName  // extra field — own @nonEmpty annotation only
) {}
