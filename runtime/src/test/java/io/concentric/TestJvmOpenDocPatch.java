package io.concentric;

import io.concentric.annotations.*;
import io.concentric.annotations.aspectOf;
import java.util.Optional;

/**
 * Aspect of TestJvmOpenDoc, which is an open contract (@contract(open = true)).
 * The aspect must still be closed — unknown fields rejected.
 * @nonEmpty is inherited from TestJvmOpenDoc.key.
 */
@aspectOf(TestJvmOpenDoc.class)
public record TestJvmOpenDocPatch(
    Optional<String> key,    // @nonEmpty inherited
    Optional<String> value   // no constraint on source field
) {}
