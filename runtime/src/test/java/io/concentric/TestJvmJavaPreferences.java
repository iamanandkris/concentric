package io.concentric;

import io.concentric.annotations.contract;
import io.concentric.annotations.reserved;

import java.util.Optional;

@contract
public record TestJvmJavaPreferences(
    @reserved Optional<String> internalSegment
) {}
