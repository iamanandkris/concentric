package io.concentric;

import io.concentric.annotations.contract;

@contract
public record TestJvmJavaInventory(
    Integer available,
    Integer reserved
) {}
