package io.concentric;

import io.concentric.annotations.*;

/**
 * Java 16+ record with a one-level @include field.
 */
@contract
public record TestJvmDocument(
    @nonEmpty String title,
              String body,
    @include  TestJvmTimestamps timestamps
) {}
