package io.concentric;

import io.concentric.annotations.*;

@contract
public record TestJvmOrder(
    @nonEmpty String orderNumber,
    TestJvmPaymentInfo paymentInfo
) {}
