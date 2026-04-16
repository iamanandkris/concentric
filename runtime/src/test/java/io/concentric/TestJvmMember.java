package io.concentric;

import io.concentric.annotations.*;

@contract
public record TestJvmMember(
    TestJvmEmail email,
    String name
) {}
