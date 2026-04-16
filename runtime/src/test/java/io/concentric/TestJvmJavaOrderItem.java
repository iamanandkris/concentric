package io.concentric;

import io.concentric.annotations.contract;

@contract
public record TestJvmJavaOrderItem(
    Long productId,
    String sku,
    Integer quantity,
    Double unitPrice
) {}
