package io.concentric

import io.concentric.annotations.*

final class DerivesContractSpec extends SpecBase:

  @contract
  case class DerivedUser(
    @nonEmpty name:  String,
    @email    email: Option[String]
  ) derives Contract

  test("Scala 3 derives Contract generates a usable contract instance") {
    val contract = summon[Contract[DerivedUser]]

    contract.validate(Map("name" -> "Alice", "email" -> "alice@example.com")).value shouldBe
      DerivedUser("Alice", Some("alice@example.com"))

    val result = contract.validate(Map("name" -> "Alice", "email" -> "not-an-email")).left.value
    result.violations.toList.exists(v =>
      v.path == FieldPath("email") && v.code == ViolationCode.ConstraintFailed("email")
    ) shouldBe true
  }

  test("Contract[T] accessor returns the in-scope contract") {
    Contract[DerivedUser]
      .validate(Map("name" -> "Bob", "email" -> "bob@example.com"))
      .value shouldBe DerivedUser("Bob", Some("bob@example.com"))
  }
