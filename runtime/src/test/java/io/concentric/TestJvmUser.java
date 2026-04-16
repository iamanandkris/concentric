package io.concentric;

import io.concentric.annotations.*;

/**
 * Java 16+ record used as a contract model in JvmContractSpec.
 *
 * Annotations are placed directly on the record components.  The Java compiler
 * propagates them to the canonical constructor parameter (@Target PARAMETER)
 * and to the backing field (@Target FIELD) automatically, so
 * JvmContractDeriver picks them up via either path.
 *
 * Covers: @immutable, @nonEmpty, @maxLength, @min, @max, @email, @masked.
 */
@contract
public record TestJvmUser(
    @immutable            Long   id,
    @nonEmpty @maxLength(50) String name,
    @min(0) @max(150)     int    age,
    @email                String email,
    @masked               String password
) {}
