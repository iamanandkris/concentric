package io.concentric;

import io.concentric.annotations.contract;
import io.concentric.annotations.email;
import io.concentric.annotations.nonEmpty;

@contract
public record TestJvmJavaUserPayload(
    @email String email,
    @nonEmpty String name,
    TestJvmJavaPreferences preferences
) {}
