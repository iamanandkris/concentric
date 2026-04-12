package io.dsentric;

import io.dsentric.annotations.*;

@contract
public record TestJvmProduct(
    @nonEmpty String sku,
    TestJvmInventory inventory
) {}
