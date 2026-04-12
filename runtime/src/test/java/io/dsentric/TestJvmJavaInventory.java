package io.dsentric;

import io.dsentric.annotations.contract;

@contract
public record TestJvmJavaInventory(
    Integer available,
    Integer reserved
) {}
