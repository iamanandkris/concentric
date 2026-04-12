package io.dsentric;

import io.dsentric.annotations.*;

@contract
public class TestKotlinNestedAddress {

    private final String city;
    private final String postcode;

    public TestKotlinNestedAddress(
        @nonEmpty String city,
        @nonEmpty String postcode
    ) {
        this.city = city;
        this.postcode = postcode;
    }

    public String city() { return city; }
    public String postcode() { return postcode; }
}
