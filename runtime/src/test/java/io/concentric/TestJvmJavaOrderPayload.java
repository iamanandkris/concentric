package io.concentric;

import io.concentric.annotations.contract;

import java.util.List;

@contract
public record TestJvmJavaOrderPayload(
    Long userId,
    String orderNumber,
    String status,
    List<TestJvmJavaOrderItem> items,
    TestJvmJavaTotals totals,
    TestJvmJavaPaymentInfo paymentInfo
) {}
