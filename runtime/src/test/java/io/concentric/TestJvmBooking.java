package io.concentric;

import io.concentric.annotations.*;

@contract
@validateContract({TestJvmBookingJavaRule.class})
public record TestJvmBooking(
    @nonEmpty long checkIn,
    @nonEmpty long checkOut,
    @nonEmpty String guestId
) {}
