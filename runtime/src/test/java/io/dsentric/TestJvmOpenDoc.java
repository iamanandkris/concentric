package io.dsentric;

import io.dsentric.annotations.*;

/**
 * Java 16+ open contract used to verify JvmContract JSON Schema export.
 */
@contract(open = true)
public record TestJvmOpenDoc(
    @nonEmpty String key,
    String value
) {}
