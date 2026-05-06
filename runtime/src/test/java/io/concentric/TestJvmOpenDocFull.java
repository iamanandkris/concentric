package io.concentric;

import io.concentric.annotations.*;

/**
 * Aspect of TestJvmOpenDoc using inherit = ALL with no exclusions.
 *
 * TestJvmOpenDoc is an open contract (@contract(open = true)).
 * This aspect declares ALL source fields (key, value) and excludes nothing.
 *
 * Tests gap #8: inherit = ALL with an open-source contract as the base.
 * Tests gap #10: inherit = ALL with no exclusions at all.
 * The resulting aspect must still be closed even though the source is open.
 */
@aspectOf(value = TestJvmOpenDoc.class,
          inherit = aspectOf.InheritMode.ALL)
public record TestJvmOpenDocFull(
    String key,    // @nonEmpty inherited from TestJvmOpenDoc.key
    String value   // no constraint on source field
) {}
