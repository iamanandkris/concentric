package io.dsentric;

public final class TestJvmBookingJavaRule implements ContractValidator<TestJvmBooking> {
    @Override
    public scala.collection.immutable.List<String> validate(TestJvmBooking value) {
        if (value.checkIn() >= value.checkOut()) {
            return scala.jdk.javaapi.CollectionConverters.asScala(java.util.List.of("checkIn must be before checkOut")).toList();
        }
        return scala.jdk.javaapi.CollectionConverters.<String>asScala(java.util.List.of()).toList();
    }
}
