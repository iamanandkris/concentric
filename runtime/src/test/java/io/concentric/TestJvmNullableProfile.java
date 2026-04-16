package io.concentric;

import io.concentric.annotations.contract;
import io.concentric.annotations.nonEmpty;
import org.jetbrains.annotations.Nullable;

@contract
public record TestJvmNullableProfile(
    @nonEmpty String username,
    @Nullable String bio,
    @Nullable TestJvmJavaPrice favoritePrice
) {}
