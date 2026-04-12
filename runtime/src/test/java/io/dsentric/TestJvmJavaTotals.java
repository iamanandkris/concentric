package io.dsentric;

import io.dsentric.annotations.contract;

@contract
public record TestJvmJavaTotals(
    Double subtotal,
    Double tax,
    Double shipping,
    Double total
) {}
