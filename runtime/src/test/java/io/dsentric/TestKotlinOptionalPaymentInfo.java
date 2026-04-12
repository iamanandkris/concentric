package io.dsentric;

import io.dsentric.annotations.contract;
import io.dsentric.annotations.internal;
import io.dsentric.annotations.masked;

import java.util.Optional;

@contract
public class TestKotlinOptionalPaymentInfo {

    private final String method;
    private final String last4;
    private final Optional<String> gatewayReference;

    public TestKotlinOptionalPaymentInfo(
        String method,
        @masked("****") String last4,
        @internal Optional<String> gatewayReference
    ) {
        this.method = method;
        this.last4 = last4;
        this.gatewayReference = gatewayReference;
    }

    public String method() { return method; }
    public String last4() { return last4; }
    public Optional<String> gatewayReference() { return gatewayReference; }
}
