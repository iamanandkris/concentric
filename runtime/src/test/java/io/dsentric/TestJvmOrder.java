package io.dsentric;

import io.dsentric.annotations.*;

@contract
public record TestJvmOrder(
    @nonEmpty String orderNumber,
    TestJvmPaymentInfo paymentInfo
) {}
