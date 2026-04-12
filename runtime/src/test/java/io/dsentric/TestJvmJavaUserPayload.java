package io.dsentric;

import io.dsentric.annotations.contract;
import io.dsentric.annotations.email;
import io.dsentric.annotations.nonEmpty;

@contract
public record TestJvmJavaUserPayload(
    @email String email,
    @nonEmpty String name,
    TestJvmJavaPreferences preferences
) {}
