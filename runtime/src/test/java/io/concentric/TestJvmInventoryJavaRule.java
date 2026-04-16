package io.concentric;

public final class TestJvmInventoryJavaRule implements ContractValidator<TestJvmInventory> {
    @Override
    public scala.collection.immutable.List<String> validate(TestJvmInventory value) {
        if (value.reserved() > value.available()) {
            return scala.jdk.javaapi.CollectionConverters.asScala(java.util.List.of("reserved must be <= available")).toList();
        }
        return scala.jdk.javaapi.CollectionConverters.<String>asScala(java.util.List.of()).toList();
    }
}
