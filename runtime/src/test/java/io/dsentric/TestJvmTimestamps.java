package io.dsentric;

import io.dsentric.annotations.*;

/**
 * Java 16+ record used for JVM @include tests.
 */
@contract
public record TestJvmTimestamps(
    @immutable Long createdAt,
               Long updatedAt
) {}
