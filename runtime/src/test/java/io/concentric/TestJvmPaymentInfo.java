package io.concentric;

import io.concentric.annotations.*;

@contract
public record TestJvmPaymentInfo(
    @masked String cardNumber,
    @internal String gatewayToken,
    @reserved String internalSegment
) {}
