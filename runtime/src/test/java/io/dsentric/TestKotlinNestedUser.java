package io.dsentric;

import io.dsentric.annotations.*;

@contract
public class TestKotlinNestedUser {

    private final long id;
    private final String name;
    private final TestKotlinNestedAddress address;

    public TestKotlinNestedUser(
        @immutable long id,
        @nonEmpty String name,
        TestKotlinNestedAddress address
    ) {
        this.id = id;
        this.name = name;
        this.address = address;
    }

    public long id() { return id; }
    public String name() { return name; }
    public TestKotlinNestedAddress address() { return address; }
}
