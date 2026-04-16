package io.concentric

final class TestJvmInventoryRule extends ContractValidator[TestJvmInventory]:
  override def validate(value: TestJvmInventory): List[String] =
    if value.reserved() > value.available() then List("reserved must be <= available")
    else Nil
