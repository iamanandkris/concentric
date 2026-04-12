package io.dsentric;

import io.dsentric.annotations.contract;

@contract
public record TestJvmJavaPrice(
    Double amount,
    String currency
) {}
