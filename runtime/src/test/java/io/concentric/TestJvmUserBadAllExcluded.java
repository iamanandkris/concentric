package io.concentric;

import io.concentric.annotations.*;

/**
 * BAD config: inherit = ALL with ALL source fields excluded and no extra fields.
 * The resulting aspect would have zero fields.
 *
 * Used only in error-case tests; JvmContract.ofAspect must throw at creation time.
 */
@aspectOf(value = TestJvmUser.class,
          inherit = aspectOf.InheritMode.ALL,
          exclude = {"id", "name", "age", "email", "password"})
public record TestJvmUserBadAllExcluded() {}
