package io.concentric;

import io.concentric.annotations.*;
import io.concentric.annotations.aspectOf;
import java.util.Optional;

/**
 * Aspect with a tighter constraint than the source.
 *
 * TestJvmUser.name has @nonEmpty @maxLength(50).
 * This aspect re-declares @maxLength(20) — the aspect value must win,
 * not the inherited one.
 */
@aspectOf(TestJvmUser.class)
public record TestJvmUserPatchStrict(
    @maxLength(20) Optional<String> name,   // overrides inherited @maxLength(50)
    Optional<String> email                  // @email inherited as usual
) {}
