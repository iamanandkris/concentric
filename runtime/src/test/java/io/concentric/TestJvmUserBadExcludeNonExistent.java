package io.concentric;

import io.concentric.annotations.*;

/**
 * BAD config: inherit = ALL with an exclude name ("nonExistentField") that
 * does not exist in TestJvmUser.
 *
 * Used only in error-case tests; JvmContract.ofAspect must throw at creation time.
 */
@aspectOf(value = TestJvmUser.class,
          inherit = aspectOf.InheritMode.ALL,
          exclude = {"age", "password", "nonExistentField"})
public record TestJvmUserBadExcludeNonExistent(
    Long   id,
    String name,
    String email
) {}
