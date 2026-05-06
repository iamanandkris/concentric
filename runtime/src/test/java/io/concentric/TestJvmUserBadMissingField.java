package io.concentric;

import io.concentric.annotations.*;

/**
 * BAD config: inherit = ALL but source field "age" and "password" are neither
 * declared in the aspect nor listed in exclude.
 *
 * Used only in error-case tests; JvmContract.ofAspect must throw at creation time.
 */
@aspectOf(value = TestJvmUser.class,
          inherit = aspectOf.InheritMode.ALL,
          exclude = {"age"})
public record TestJvmUserBadMissingField(
    Long   id,
    String name,
    String email
    // password is missing from both aspect and exclude — should fail
) {}
