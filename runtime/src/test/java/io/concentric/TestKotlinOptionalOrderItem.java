package io.concentric;

import io.concentric.annotations.contract;

@contract
public class TestKotlinOptionalOrderItem {

    private final Long productId;
    private final String sku;
    private final Integer quantity;
    private final Double unitPrice;

    public TestKotlinOptionalOrderItem(
        Long productId,
        String sku,
        Integer quantity,
        Double unitPrice
    ) {
        this.productId = productId;
        this.sku = sku;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public Long productId() { return productId; }
    public String sku() { return sku; }
    public Integer quantity() { return quantity; }
    public Double unitPrice() { return unitPrice; }
}
