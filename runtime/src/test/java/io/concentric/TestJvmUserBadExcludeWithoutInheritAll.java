package io.concentric;

import io.concentric.annotations.*;
import java.util.Optional;

/**
 * BAD config: exclude specified without inherit = ALL (uses default EXPLICIT mode).
 *
 * Used only in error-case tests; JvmContract.ofAspect must throw at creation time.
 */
@aspectOf(value = TestJvmUser.class,
          exclude = {"age"})
public record TestJvmUserBadExcludeWithoutInheritAll(
    Optional<String> name,
    Optional<String> email
) {}
