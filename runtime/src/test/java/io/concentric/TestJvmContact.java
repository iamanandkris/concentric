package io.concentric;

import io.concentric.annotations.*;

@contract
public record TestJvmContact(
    @validateWith({TestJvmPhoneValidator.class}) String phone,
    String name
) {}
