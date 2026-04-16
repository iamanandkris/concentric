package io.concentric;

import io.concentric.annotations.contract;

@contract
public record TestJvmJavaPrice(
    Double amount,
    String currency
) {}
