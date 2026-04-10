package io.dsentric;

import io.dsentric.annotations.*;

/**
 * Java 16+ record with a one-level @include field.
 */
@contract
public record TestJvmDocument(
    @nonEmpty String title,
              String body,
    @include  TestJvmTimestamps timestamps
) {}
