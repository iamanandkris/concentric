package io.concentric;

import io.concentric.annotations.contract;
import io.concentric.annotations.internal;
import io.concentric.annotations.masked;

import java.util.Optional;

@contract
public record TestJvmJavaPaymentInfo(
    String method,
    @masked("****") String last4,
    @internal Optional<String> gatewayReference
) {}
