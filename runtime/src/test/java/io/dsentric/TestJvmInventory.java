package io.dsentric;

import io.dsentric.annotations.*;

@contract
@validateContract({TestJvmInventoryJavaRule.class})
public record TestJvmInventory(
    @immutable String warehouseId,
    @min(0) int available,
    @min(0) int reserved
) {}
