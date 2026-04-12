package io.dsentric;

import io.dsentric.annotations.contract;

import java.util.List;

@contract
public class TestKotlinOptionalOrderPayload {

    private final Long userId;
    private final String orderNumber;
    private final String status;
    private final List<TestKotlinOptionalOrderItem> items;
    private final TestKotlinOptionalTotals totals;
    private final TestKotlinOptionalPaymentInfo paymentInfo;

    public TestKotlinOptionalOrderPayload(
        Long userId,
        String orderNumber,
        String status,
        List<TestKotlinOptionalOrderItem> items,
        TestKotlinOptionalTotals totals,
        TestKotlinOptionalPaymentInfo paymentInfo
    ) {
        this.userId = userId;
        this.orderNumber = orderNumber;
        this.status = status;
        this.items = items;
        this.totals = totals;
        this.paymentInfo = paymentInfo;
    }

    public Long userId() { return userId; }
    public String orderNumber() { return orderNumber; }
    public String status() { return status; }
    public List<TestKotlinOptionalOrderItem> items() { return items; }
    public TestKotlinOptionalTotals totals() { return totals; }
    public TestKotlinOptionalPaymentInfo paymentInfo() { return paymentInfo; }
}
