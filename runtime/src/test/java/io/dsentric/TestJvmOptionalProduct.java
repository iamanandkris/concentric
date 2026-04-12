package io.dsentric;

import io.dsentric.annotations.*;
import java.util.Optional;

@contract
public record TestJvmOptionalProduct(
    @nonEmpty String sku,
    Optional<TestJvmInventory> inventory
) {}
