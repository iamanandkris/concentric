package io.concentric

final class TestJvmBookingRule extends ContractValidator[TestJvmBooking]:
  override def validate(value: TestJvmBooking): List[String] =
    if value.checkIn() >= value.checkOut() then List("checkIn must be before checkOut")
    else Nil
