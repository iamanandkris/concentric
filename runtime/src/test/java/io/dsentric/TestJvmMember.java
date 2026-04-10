package io.dsentric;

import io.dsentric.annotations.*;

@contract
public record TestJvmMember(
    TestJvmEmail email,
    String name
) {}
