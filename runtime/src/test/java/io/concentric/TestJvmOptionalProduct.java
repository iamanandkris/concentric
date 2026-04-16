package io.concentric;

import io.concentric.annotations.*;
import java.util.Optional;

@contract
public record TestJvmOptionalProduct(
    @nonEmpty String sku,
    Optional<TestJvmInventory> inventory
) {}
