package io.concentric;

import io.concentric.annotations.*;

@contract
public record TestJvmProduct(
    @nonEmpty String sku,
    TestJvmInventory inventory
) {}
