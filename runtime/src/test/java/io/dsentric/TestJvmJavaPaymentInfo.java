package io.dsentric;

import io.dsentric.annotations.contract;
import io.dsentric.annotations.internal;
import io.dsentric.annotations.masked;

import java.util.Optional;

@contract
public record TestJvmJavaPaymentInfo(
    String method,
    @masked("****") String last4,
    @internal Optional<String> gatewayReference
) {}
