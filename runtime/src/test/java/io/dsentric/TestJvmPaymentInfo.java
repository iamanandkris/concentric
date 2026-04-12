package io.dsentric;

import io.dsentric.annotations.*;

@contract
public record TestJvmPaymentInfo(
    @masked String cardNumber,
    @internal String gatewayToken,
    @reserved String internalSegment
) {}
