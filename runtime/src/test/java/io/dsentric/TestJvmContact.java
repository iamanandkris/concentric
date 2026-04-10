package io.dsentric;

import io.dsentric.annotations.*;

@contract
public record TestJvmContact(
    @validateWith({TestJvmPhoneValidator.class}) String phone,
    String name
) {}
