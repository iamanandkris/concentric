package io.dsentric;

import io.dsentric.annotations.contract;
import io.dsentric.annotations.nonEmpty;
import org.jetbrains.annotations.Nullable;

@contract
public record TestJvmNullableProfile(
    @nonEmpty String username,
    @Nullable String bio,
    @Nullable TestJvmJavaPrice favoritePrice
) {}
