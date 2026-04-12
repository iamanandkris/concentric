package io.dsentric;

import io.dsentric.annotations.contract;

@contract
public record TestJvmJavaOrderItem(
    Long productId,
    String sku,
    Integer quantity,
    Double unitPrice
) {}
