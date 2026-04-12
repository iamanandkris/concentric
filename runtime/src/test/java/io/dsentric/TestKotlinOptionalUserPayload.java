package io.dsentric;

import io.dsentric.annotations.contract;
import io.dsentric.annotations.email;
import io.dsentric.annotations.internal;
import io.dsentric.annotations.nonEmpty;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@contract
public class TestKotlinOptionalUserPayload {

    private final String email;
    private final String name;
    private final TestKotlinOptionalAddress address;
    private final TestKotlinOptionalPreferences preferences;
    private final Optional<String> internalNotes;

    public TestKotlinOptionalUserPayload(
        @email String email,
        @nonEmpty String name,
        @Nullable TestKotlinOptionalAddress address,
        @Nullable TestKotlinOptionalPreferences preferences,
        @internal Optional<String> internalNotes
    ) {
        this.email = email;
        this.name = name;
        this.address = address;
        this.preferences = preferences;
        this.internalNotes = internalNotes;
    }

    public String email() { return email; }
    public String name() { return name; }
    public TestKotlinOptionalAddress address() { return address; }
    public TestKotlinOptionalPreferences preferences() { return preferences; }
    public Optional<String> internalNotes() { return internalNotes; }
}
