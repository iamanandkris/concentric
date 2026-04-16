package io.concentric;

import io.concentric.annotations.contract;
import org.jetbrains.annotations.Nullable;

@contract
public class TestKotlinOptionalAddress {

    private final String street;
    private final String city;
    private final String zipCode;

    public TestKotlinOptionalAddress(
        @Nullable String street,
        @Nullable String city,
        @Nullable String zipCode
    ) {
        this.street = street;
        this.city = city;
        this.zipCode = zipCode;
    }

    public String street() { return street; }
    public String city() { return city; }
    public String zipCode() { return zipCode; }
}
