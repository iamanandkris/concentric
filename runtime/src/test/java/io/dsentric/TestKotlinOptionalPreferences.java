package io.dsentric;

import io.dsentric.annotations.contract;
import io.dsentric.annotations.reserved;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@contract
public class TestKotlinOptionalPreferences {

    private final Boolean newsletter;
    private final Optional<String> internalSegment;

    public TestKotlinOptionalPreferences(
        @Nullable Boolean newsletter,
        @reserved Optional<String> internalSegment
    ) {
        this.newsletter = newsletter;
        this.internalSegment = internalSegment;
    }

    public Boolean newsletter() { return newsletter; }
    public Optional<String> internalSegment() { return internalSegment; }
}
