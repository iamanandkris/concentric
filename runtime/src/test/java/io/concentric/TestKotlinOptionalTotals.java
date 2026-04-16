package io.concentric;

import io.concentric.annotations.contract;

@contract
public class TestKotlinOptionalTotals {

    private final Double subtotal;
    private final Double tax;
    private final Double shipping;
    private final Double total;

    public TestKotlinOptionalTotals(
        Double subtotal,
        Double tax,
        Double shipping,
        Double total
    ) {
        this.subtotal = subtotal;
        this.tax = tax;
        this.shipping = shipping;
        this.total = total;
    }

    public Double subtotal() { return subtotal; }
    public Double tax() { return tax; }
    public Double shipping() { return shipping; }
    public Double total() { return total; }
}
