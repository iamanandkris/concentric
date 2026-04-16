package io.concentric;

import io.concentric.annotations.*;

/**
 * Java 16+ record with @reserved — used for patch-semantics tests.
 */
@contract
public record TestJvmTicket(
    @reserved String trackingId,
    @nonEmpty String title
) {}
