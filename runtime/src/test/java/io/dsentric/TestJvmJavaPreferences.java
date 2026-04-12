package io.dsentric;

import io.dsentric.annotations.contract;
import io.dsentric.annotations.reserved;

import java.util.Optional;

@contract
public record TestJvmJavaPreferences(
    @reserved Optional<String> internalSegment
) {}
