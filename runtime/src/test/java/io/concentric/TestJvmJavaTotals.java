package io.concentric;

import io.concentric.annotations.contract;

@contract
public record TestJvmJavaTotals(
    Double subtotal,
    Double tax,
    Double shipping,
    Double total
) {}
