package io.concentric;

import io.concentric.annotations.*;
import java.util.Optional;

/**
 * Java 16+ record with optional fields — used to exercise the
 * {@code java.util.Optional<T>} code path in {@link JvmContract}.
 *
 * <ul>
 *   <li>{@code username} — required, @nonEmpty</li>
 *   <li>{@code bio}      — optional String (may be absent from the raw map)</li>
 *   <li>{@code score}    — optional Integer with @min(0) constraint</li>
 * </ul>
 */
@contract
public record TestJvmProfile(
    @nonEmpty String           username,
              Optional<String> bio,
    @min(0)   Optional<Integer> score
) {}
