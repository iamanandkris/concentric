# concentric

A compile-time, annotation-driven validation and contract library for Scala 3, with full interop support for Java and Kotlin.

concentric turns an annotated case class (or Java record / Kotlin data class) into a **Contract** — a reusable object that validates, sanitizes, patches, and serialises structured data. Every operation accumulates *all* violations rather than short-circuiting on the first failure, and every violation carries a structured code that is easy to map to HTTP status codes or API error envelopes.

---

## Table of Contents

### Getting Started

- [Installation](#installation)
- [Defining a contract](#defining-a-contract)
- [Cross-feature examples](#cross-feature-examples)

### Core Operations

- [validate — full object validation](#validate--full-object-validation)
- [Decision Logic On Validated Values](#decision-logic-on-validated-values)
- [validatePatch — partial update validation](#validatepatch--partial-update-validation)
- [validatePartial and Draft — multi-step form validation](#validatepartial-and-draft--multi-step-form-validation)
- [collectViolations — violations without construction](#collectviolations--violations-without-construction)
- [sanitize — safe output representation](#sanitize--safe-output-representation)
- [toRaw — serialize back to a raw map](#toraw--serialize-back-to-a-raw-map)
- [Patch — type-safe partial updates](#patch--type-safe-partial-updates)
- [Filter — type-safe query expressions](#filter--type-safe-query-expressions)
- [View — composable output transforms](#view--composable-output-transforms)

### Advanced Features

- [Optional / nullable fields](#optional--nullable-fields)
- [ContractValidator — cross-field rules](#contractvalidator--cross-field-rules)
- [Custom field validators](#custom-field-validators)
- [@include — inline nested types](#include--inline-nested-types)
- [@discriminator — tagged-union wire format](#discriminator--tagged-union-wire-format)
- [@decodable and @extract — structured string types](#decodable-and-extract--structured-string-types)
- [Open contracts](#open-contracts)
- [Aspects — structural variants of a contract](#aspects--structural-variants-of-a-contract)
- [jsonSchema — derive a JSON Schema document](#jsonschema--derive-a-json-schema-document)

### Reference

- [Java and Kotlin interop](#java-and-kotlin-interop)
- [Core concepts](#core-concepts)
- [Validation annotations reference](#validation-annotations-reference)
- [Violation codes reference](#violation-codes-reference)
- [RawDecoder — supported field types](#rawdecoder--supported-field-types)

---

## Installation

Add the following to your `build.sbt`:

```scala
// Annotations (pure Java — works for Scala, Java, and Kotlin projects)
libraryDependencies += "io.concentric" % "concentric-annotations" % "0.1.0"

// Core library (Scala 3, no effect dependency)
libraryDependencies += "io.concentric" %% "concentric-core" % "0.1.0"

// Java/Kotlin reflection-based interop (no macro required)
libraryDependencies += "io.concentric" %% "concentric-runtime" % "0.1.0"
```

The `concentric-core` module requires **Scala 3** and has no runtime effect dependency. The `concentric-annotations` module is pure Java 11+ and has no Scala dependency. The `concentric-runtime` module provides `JvmContract[T]` for Java and Kotlin callers.

---

## Defining a contract

### Scala

Annotate a case class with `@contract` and derive the contract with `Contract.derived[T]`:

```scala
import io.concentric.annotations.*
import io.concentric.*

@contract
case class User(
  @immutable @internal          id:       Long,
  @nonEmpty  @maxLength(100)    name:     String,
  @email                        email:    String,
  @min(0)    @max(150)          age:      Int = 0,
  @masked                       password: Option[String] = None
) derives Contract
```

That's it. The macro reads every annotation at **compile time** and generates a zero-overhead validator.

<details>
<summary>Java</summary>

```java
import io.concentric.annotations.*;
import io.concentric.JvmContract;

@contract
public record User(
    @immutable @internal            Long   id,
    @nonEmpty  @maxLength(100)      String name,
    @email                          String email,
    @min(0)    @max(150)            int    age,
    @masked                         String password   // null = absent
) {}

// Zero boilerplate — the library builds the constructor automatically (Java 16+):
static final JvmContract<User> userContract = JvmContract.ofRecord(User.class);
```

`JvmContract.ofRecord` reads the record's canonical constructor via `getRecordComponents()`, coerces every field value to its declared type, and invokes the constructor — all with no code from you. See [Java and Kotlin interop](#java-and-kotlin-interop) for details and the fallback `JvmContract.of(...)` form.

</details>

<details>
<summary>Kotlin</summary>

```kotlin
import io.concentric.annotations.*
import io.concentric.JvmContract

@contract
data class User(
    @field:immutable @field:internal val id:       Long,
    @field:nonEmpty  @field:maxLength(100) val name: String,
    @field:email                     val email:    String,
    @field:min(0)    @field:max(150)  val age:      Int = 0,
    @field:masked                    val password: String? = null
)

// Zero boilerplate — library discovers the primary constructor automatically:
val userContract: JvmContract<User> = JvmContract.ofPrimary(User::class.java)
```

`JvmContract.ofPrimary` finds the primary (non-synthetic) constructor, skipping Kotlin's synthetic default-argument constructor. See [Java and Kotlin interop](#java-and-kotlin-interop) for details and the fallback `JvmContract.of(...)` form.

</details>

---

## Cross-feature examples

### User registration API (validate + sanitize + toRaw)

```scala
@contract
case class UserRegistration(
  @reserved @internal         id:       Long = 0L,
  @nonEmpty @maxLength(50)    username: String,
  @email                      email:    String,
  @min(18)                    age:      Int,
  @masked                     password: String
) derives Contract

val regContract = summon[Contract[UserRegistration]]

// 1. Validate incoming request body
def register(raw: RawObject): Either[ContractViolations, UserRegistration] =
  regContract.validate(raw)

// 2. After persisting, sanitize before returning to client
def toResponse(raw: RawObject): RawObject =
  regContract.sanitize(raw)
  // Result: id is gone (@internal), password is "***" (@masked)
```

### Profile update API (validatePatch + Patch builder)

```scala
@contract
case class UserProfile(
  @immutable @internal    id:       Long,
  @nonEmpty @maxLength(50) name:    String,
  @email                  email:    String,
  @min(0)  @max(150)      age:      Int,
                          bio:      Option[String] = None
) derives Contract

val profileContract = summon[Contract[UserProfile]]

def updateProfile(
  currentRaw: RawObject,
  nameOpt:    Option[String],
  ageOpt:     Option[Int],
  clearBio:   Boolean
): Either[ContractViolations, UserProfile] =
  val patch = Patch.empty[UserProfile]
    .pipe(p => nameOpt.fold(p)(n => p.set(_.name, n)))
    .pipe(p => ageOpt.fold(p)(a => p.set(_.age, a)))
    .pipe(p => if clearBio then p.unset(_.bio) else p)
  profileContract.applyPatch(currentRaw, patch)
```

### Multi-step checkout form (validatePartial + Draft)

```scala
@contract
case class Order(
  @immutable @internal    orderId:     String,
  @nonEmpty               customerId:  String,
  @nonEmpty               productId:   String,
  @positive               quantity:    Int,
  @nonEmpty               shippingAddr: String,
  @email                  contactEmail: String
) derives Contract

val orderContract = summon[Contract[Order]]

def processCheckout(
  step1: RawObject,  // customerId, productId, quantity
  step2: RawObject   // shippingAddr, contactEmail
): Either[ContractViolations, Order] =
  for
    draft1 <- orderContract.validatePartial(step1)
    draft2 <- orderContract.validatePartial(step2)
    order  <- draft1.merge(draft2).finalize(orderContract)
  yield order
```

### Query + in-memory filtering + MongoDB

```scala
val f = userContract.filter

// Find adult users named Alice or Bob with a verified email
val expr: FilterExpr[User] =
  (f.field(_.name).is("Alice") || f.field(_.name).is("Bob")) &&
  f.field(_.age).gte(18) &&
  f.field(_.email).exists

// In-memory test
val matching: List[RawObject] = expr(allUsers)

// MongoDB query
collection.find(expr.toMongoQuery)
// → Map(
//     "$or"   -> List(Map("name" -> "Alice"), Map("name" -> "Bob")),
//     "age"   -> Map("$gte"    -> 18),
//     "email" -> Map("$exists" -> true)
//   )
```

### Structured date parsing with @extract + @contract

```scala
@decodable
@extract("(\\d{4})-(\\d{2})-(\\d{2})")
case class IsoDate(year: Int, month: Int, day: Int)
given RawDecoder[IsoDate] = RawDecoder.derived[IsoDate]

class EventDateValidator extends ContractValidator[Event]:
  def validate(e: Event): List[String] =
    if e.startDate.year > 2100
    then List("Event cannot be scheduled past year 2100")
    else Nil

@contract
@validateContract(Array(classOf[EventDateValidator]))
case class Event(
  @nonEmpty               name:      String,
                          startDate: IsoDate,
                          endDate:   IsoDate
) derives Contract

val eventContract = summon[Contract[Event]]

eventContract.validate(Map(
  "name"      -> "Conference",
  "startDate" -> "2024-06-15",   // decoded to IsoDate(2024, 6, 15)
  "endDate"   -> "2024-06-17"    // decoded to IsoDate(2024, 6, 17)
))
```

### @include + sanitize for audit-stamped records

```scala
@contract
case class AuditStamp(
  @immutable @internal  createdBy: String,
  @immutable @internal  createdAt: Long,
                        updatedAt: Long
) derives Contract

@contract
case class Document(
  @immutable @internal  id:        Long,
  @nonEmpty             title:     String,
  @nonEmpty             body:      String,
  @include              audit:     AuditStamp
) derives Contract

val auditContract = summon[Contract[AuditStamp]]
val documentContract = summon[Contract[Document]]

// Wire format is flat:
// {"id":1, "title":"...", "body":"...", "createdBy":"admin", "createdAt":..., "updatedAt":...}

// sanitize strips createdBy, createdAt, id (all @internal from included type):
val safe = documentContract.sanitize(storedRaw)
// → {"title":"...", "body":"...", "updatedAt":...}
```

### Full Java record example with all operations

<details>
<summary>Java — full CRUD contract lifecycle</summary>

```java
import io.concentric.annotations.*;
import io.concentric.*;
import java.util.*;

@contract
public record Product(
    @immutable @internal        Long   id,
    @nonEmpty  @maxLength(100)  String name,
    @nonEmpty                   String sku,
    @positive                   double price,
    @min(0)                     int    stockLevel,
    @masked                     String internalCost
) {}

public class ProductService {

    // Zero boilerplate — library auto-discovers the canonical constructor:
    static final JvmContract<Product> contract = JvmContract.ofRecord(Product.class);

    // Create
    public ValidationResult<Product> create(Map<String, Object> raw) {
        return contract.validate(raw);
    }

    // Update (patch)
    public ValidationResult<Product> update(
        Map<String, Object> current,
        Map<String, Object> patch
    ) {
        return contract.validatePatch(current, patch);
    }

    // Safe output (strips id, masks internalCost)
    public Map<String, Object> toPublic(Map<String, Object> stored) {
        return contract.sanitize(stored);
    }

    // Per-field validation for UI forms
    public List<JvmViolation> validateField(String field, Object value) {
        return contract.validatePartial(Map.of(field, value));
    }
}
```

</details>

<details>
<summary>Kotlin — full data class lifecycle</summary>

```kotlin
import io.concentric.JvmContract
import io.concentric.JvmViolation
import io.concentric.ValidationResult
import io.concentric.annotations.*

@contract
data class Product(
    @field:immutable @field:internal val id: Long,
    @field:nonEmpty  @field:maxLength(100) val name: String,
    @field:nonEmpty val sku: String,
    @field:positive val price: Double,
    @field:min(0) val stockLevel: Int,
    @field:masked val internalCost: String?
)

class ProductService {

    private val contract: JvmContract<Product> = JvmContract.ofPrimary(Product::class.java)

    fun create(raw: Map<String, Any>): ValidationResult<Product> =
        contract.validate(raw)

    fun update(
        current: Map<String, Any>,
        patch: Map<String, Any>
    ): ValidationResult<Product> =
        contract.validatePatch(current, patch)

    fun toPublic(stored: Map<String, Any>): Map<String, Any> =
        contract.sanitize(stored)

    fun validateField(field: String, value: Any): List<JvmViolation> =
        contract.validatePartial(mapOf(field to value))
}
```

</details>

---


## validate — full object validation

`validate` takes a `RawObject` (a `Map[String, Any]`) and runs the complete validation pipeline:

1. Check required fields are present.
2. Decode each field to the declared type.
3. Apply constraint annotations.
4. Check `@reserved` fields are absent.
5. Reject unknown fields (for closed contracts).
6. Construct and return `T`.

All violations are accumulated — it never short-circuits.

### Scala

```scala
import io.concentric.*

val raw: RawObject = Map(
  "id"       -> 1L,
  "name"     -> "Alice",
  "email"    -> "alice@example.com",
  "age"      -> 30,
  "password" -> "s3cr3t"
)

// Either-based
val effect: Either[ContractViolations, User] = userContract.validate(raw)

// Synchronous (outside any effect system)
userContract.validateEither(raw) match
  case Right(user) => println(s"Valid: ${user.name}")
  case Left(cvs)   =>
    cvs.violations.foreach(v => println(s"[${v.path}] ${v.message}"))
```

**Missing required field:**

```scala
val incomplete = Map("id" -> 1L, "name" -> "Alice", "email" -> "alice@example.com")
// age is missing — produces: ExpectedMissing at "age"
```

**Type mismatch:**

```scala
val badType = Map("id" -> 1L, "name" -> 42, "email" -> "a@b.com", "age" -> 30)
// name is Int, not String — produces: TypeMismatch("String", "Int") at "name"
```

**Constraint failure:**

```scala
val badValue = Map("id" -> 1L, "name" -> "", "email" -> "a@b.com", "age" -> 30)
// name is empty — produces: ConstraintFailed("nonEmpty") at "name"
```

**Multiple violations at once:**

```scala
val bad = Map("id" -> 1L, "name" -> "", "email" -> "not-an-email", "age" -> -1)
// Three violations in one call:
//   ConstraintFailed("nonEmpty")  at "name"
//   ConstraintFailed("email")     at "email"
//   ConstraintFailed("min")       at "age"
```

## Decision Logic On Validated Values

Once `validate` succeeds, you have an ordinary typed Scala value. From that point onward, decision logic can use standard Scala pattern matching — including named case-class patterns and nested matches — without any concentric-specific API.

This is especially useful when you only care about a few fields from a larger contract.

```scala
@contract
case class Address(
  street: String,
  city:   String,
  country: String
) derives Contract

@contract
case class User(
  @immutable @internal id: Long,
  @nonEmpty            name: String,
                       email: Option[String],
                       address: Address,
                       age: Int = 0
) derives Contract
```

After validation:

```scala
val raw: RawObject = Map(
  "id"      -> 42L,
  "name"    -> "Ana",
  "email"   -> "ana@example.com",
  "age"     -> 30,
  "address" -> Map(
    "street"  -> "221B Baker Street",
    "city"    -> "London",
    "country" -> "UK"
  )
)

val user = Contract[User].validate(raw).toOption.get
```

You can match only on the `id` even though it is marked `@internal`:

```scala
user match
  case User(id = 42L) =>
    println("special internal user")
  case User(id = otherId) =>
    println(s"ordinary user: $otherId")
```

You can also match on both outer and inner contract fields:

```scala
user match
  case User(id = 42L, address = Address(city = "London")) =>
    println("London-based internal user")

  case User(address = Address(country = "UK")) =>
    println("UK user")

  case User(email = Some(email)) =>
    println(s"user with email $email")

  case User(email = None) =>
    println("user has no email")
```

### Kotlin and Java

The same idea works on Kotlin and Java too: concentric still validates raw input into typed nested values, so decision logic can use normal property access instead of stringly-typed map traversal. The main difference is that Scala has richer pattern matching syntax.

**Kotlin:**

```kotlin
val result = contract.validate(raw)
if (result.isValid) {
    val user = result.value!!

    when {
        user.id == 42L && user.address.city == "London" ->
            println("London internal user")

        user.email != null ->
            println("user with email ${user.email}")

        else ->
            println("fallback")
    }
}
```

**Java:**

```java
ValidationResult<User> result = contract.validate(raw);
if (result.isValid()) {
    User user = result.getValue().get();

    if (user.id() == 42L && user.address().city().equals("London")) {
        System.out.println("London internal user");
    } else if (user.email() != null) {
        System.out.println("user with email " + user.email());
    } else {
        System.out.println("fallback");
    }
}
```

So the experience is:

- Scala: most concise, because named case-class patterns can match outer and inner fields directly.
- Kotlin: still expressive, but more predicate-style with `when` and property checks.
- Java: straightforward and typed, but more verbose.

Two important notes:

- `@internal` affects `sanitize` output, not the in-memory Scala value. If validation succeeds, the field is still available for pattern matching.
- Nested contracts become ordinary nested case class values after validation, so Scala handles them naturally.

<details>
<summary>Java</summary>

```java
Map<String, Object> raw = Map.of(
    "id",    1L,
    "name",  "Alice",
    "email", "alice@example.com",
    "age",   30
);

ValidationResult<User> result = userContract.validate(raw);

if (result.isValid()) {
    User user = result.getValue().get();
    System.out.println("Valid: " + user.name());
} else {
    result.getErrors().forEach(v ->
        System.out.printf("[%s] %s%n", v.path, v.message)
    );
}
```

</details>

<details>
<summary>Kotlin</summary>

```kotlin
val raw = mapOf(
    "id"    to 1L,
    "name"  to "Alice",
    "email" to "alice@example.com",
    "age"   to 30
)

val result = userContract.validate(raw)

if (result.isValid) {
    println("Valid: ${result.value.get().name}")
} else {
    result.errors.forEach { v -> println("[${v.path}] ${v.message}") }
}
```

</details>

---

## validatePatch — partial update validation

`validatePatch` validates a partial update (patch) against the currently stored state. Fields present in the patch are fully validated; fields absent from the patch are taken from `currentRaw` without re-validation.

Key behaviors:
- `@immutable` fields in the patch → `IMMUTABLE` violation.
- `@reserved` fields in the patch → `RESERVED` violation.
- All constraint annotations (`@min`, `@email`, etc.) are enforced on patched values.
- The merged result must satisfy all required-field checks.

### Scala

```scala
val current: RawObject = Map(
  "id"    -> 1L,
  "name"  -> "Alice",
  "email" -> "alice@example.com",
  "age"   -> 30
)

// Valid patch — only name and age are changing
val patch: RawObject = Map("name" -> "Alicia", "age" -> 31)
userContract.validatePatch(current, patch)
// → Right(User(id=1, name="Alicia", email="alice@example.com", age=31))

// @immutable field in patch → rejected
val illegalPatch = Map("id" -> 99L, "name" -> "Alicia")
userContract.validatePatch(current, illegalPatch)
// → Left(ContractViolations): ImmutableField at "id"

// Constraint violation on patched field
val badPatch = Map("age" -> -5)
userContract.validatePatch(current, badPatch)
// → Left(ContractViolations): ConstraintFailed("min") at "age"
```

<details>
<summary>Java</summary>

```java
Map<String, Object> current = Map.of(
    "id", 1L, "name", "Alice", "email", "alice@example.com", "age", 30
);
Map<String, Object> patch = Map.of("name", "Alicia", "age", 31);

ValidationResult<User> result = userContract.validatePatch(current, patch);

// Attempt to change immutable id:
Map<String, Object> badPatch = Map.of("id", 99L);
ValidationResult<User> bad = userContract.validatePatch(current, badPatch);
// bad.getErrors() contains JvmViolation with code "IMMUTABLE" at path "id"
```

</details>

<details>
<summary>Kotlin</summary>

```kotlin
val current = mapOf("id" to 1L, "name" to "Alice", "email" to "alice@example.com", "age" to 30)
val patch    = mapOf("name" to "Alicia", "age" to 31)

val result = userContract.validatePatch(current, patch)

// Immutable field attempt:
val badPatch = mapOf("id" to 99L)
val bad = userContract.validatePatch(current, badPatch)
// bad.errors contains "IMMUTABLE" violation for "id"
```

</details>

---

## validatePartial and Draft — multi-step form validation

`validatePartial` runs the full constraint pipeline on whatever fields are present, but does **not** fail for absent required fields. It returns a `Draft[T]` — a container of validated field values that can be accumulated across multiple steps.

This is ideal for multi-page forms, API endpoints that accept partial payloads, or any scenario where you want per-step feedback before the object is complete.

### Step-by-step form

```scala
for
  // Step 1: user fills in identity fields
  draft1 <- userContract.validatePartial(
    Map("id" -> 1L, "name" -> "Alice", "email" -> "alice@example.com")
  )

  // Step 2: user fills in profile details
  draft2 <- userContract.validatePartial(
    Map("age" -> 30, "password" -> "s3cr3t")
  )

  // Step 3: merge and finalize
  user <- draft1.merge(draft2).finalize(userContract)
yield user
```

### Partial without finalize — just get per-field errors

```scala
// Validate a single field during interactive input
userContract.validatePartial(Map("email" -> "not-valid"))
// → Left: ConstraintFailed("email") at "email"
// Note: no ExpectedMissing for the other required fields

userContract.validatePartial(Map("email" -> "alice@example.com"))
// → Right(Draft[email])  — email field is valid, no other fields checked
```

### @immutable in validatePartial

`@immutable` is **not** enforced during `validatePartial` or `validate`. It is only checked during `validatePatch`. This means you can supply an `@immutable` field in the initial creation call without any error.

```scala
// Fine — @immutable only matters for future patch operations
userContract.validatePartial(Map("id" -> 1L))
// → Right(Draft[id])
```

<details>
<summary>Java</summary>

```java
// Step 1
List<JvmViolation> step1Errors = userContract.validatePartial(
    Map.of("id", 1L, "name", "Alice", "email", "alice@example.com")
);
// step1Errors is empty → all supplied fields are valid

// Single field validation (during live input)
List<JvmViolation> emailErrors = userContract.validatePartial(
    Map.of("email", "not-valid")
);
// emailErrors contains: code="CONSTRAINT(email)", path="email"

// Full validation once the form is complete:
ValidationResult<User> result = userContract.validate(completeMap);
```

</details>

<details>
<summary>Kotlin</summary>

```kotlin
// Single-field live validation
val emailErrors = userContract.validatePartial(mapOf("email" to "bad"))
// Returns non-empty List<JvmViolation> for the email field

// All fields valid so far, no missing-field errors:
val ok = userContract.validatePartial(mapOf("id" to 1L, "name" to "Alice"))
// Returns emptyList()
```

</details>

---

## collectViolations — violations without construction

`collectViolations` runs the complete validation pipeline and returns all violations, but does **not** attempt to construct `T`. Useful when you only need the error list for custom reporting pipelines or audit logs.

```scala
val raw: RawObject = Map(
  "id"    -> 1L,
  "name"  -> "",          // @nonEmpty violation
  "age"   -> -5,          // @min(0) violation
  "email" -> "not-valid"  // @email violation
  // password absent — @masked optional, no violation
)

val violations: List[Violation] = userContract.collectViolations(raw)
// Returns all violations at once — never short-circuits:
// Violation(FieldPath("name"),  ConstraintFailed("nonEmpty"), "name must not be empty")
// Violation(FieldPath("age"),   ConstraintFailed("min"),      "age must be >= 0")
// Violation(FieldPath("email"), ConstraintFailed("email"),    "email must be a valid email address")

// Access each violation's fields:
violations.foreach { v =>
  println(s"[${v.path}] ${v.code} — ${v.message}")
}

// Build a field-keyed error map (useful for API responses):
val errorMap: Map[String, List[String]] =
  violations
    .groupBy(_.path.toString)
    .view.mapValues(_.map(_.message))
    .toMap
// Map("name" -> List("must not be empty"), "age" -> List("must be >= 0"), ...)

// Check for a specific code programmatically:
val hasMissing = violations.exists(_.code == ViolationCode.ExpectedMissing)
val hasEmail   = violations.exists(_.code == ViolationCode.ConstraintFailed("email"))
```

Empty list means the raw object is fully valid (though `T` is not constructed). Use `validate` or `validateEither` when you need the typed result.

<details>
<summary>Java</summary>

```java
Map<String, Object> raw = Map.of(
    "id",    1L,
    "name",  "",           // @nonEmpty violation
    "age",   -5,           // @min(0) violation
    "email", "not-valid"   // @email violation
);

List<JvmViolation> violations = userContract.collectViolations(raw);
// JvmViolation has three plain String fields: path, code, message

for (JvmViolation v : violations) {
    System.out.printf("[%s] %s — %s%n", v.path, v.code, v.message);
}
// [name]  CONSTRAINT(nonEmpty) — name must not be empty
// [age]   CONSTRAINT(min)      — age must be >= 0
// [email] CONSTRAINT(email)    — email must be a valid email address

// Build a field-keyed error map for an API response:
Map<String, List<String>> errorMap = violations.stream()
    .collect(Collectors.groupingBy(
        v -> v.path,
        Collectors.mapping(v -> v.message, Collectors.toList())
    ));
```

</details>

<details>
<summary>Kotlin</summary>

```kotlin
val raw = mapOf(
    "id"    to 1L,
    "name"  to "",          // @nonEmpty violation
    "age"   to -5,          // @min(0) violation
    "email" to "not-valid"  // @email violation
)

val violations: List<JvmViolation> = userContract.collectViolations(raw)

violations.forEach { v ->
    println("[${v.path}] ${v.code} — ${v.message}")
}
// [name]  CONSTRAINT(nonEmpty) — name must not be empty
// [age]   CONSTRAINT(min)      — age must be >= 0
// [email] CONSTRAINT(email)    — email must be a valid email address

// Group into a field → messages map:
val errorMap: Map<String, List<String>> = violations
    .groupBy { it.path }
    .mapValues { (_, vs) -> vs.map { it.message } }
```

</details>

---

## sanitize — safe output representation

`sanitize` takes a raw map and applies two transformations without running validation:

- `@internal` fields are **removed** entirely.
- `@masked` fields have their values **replaced** with the mask string.

Use this before sending data over the wire to external consumers.

```scala
@contract
case class User(
  @immutable @internal          id:       Long,
  @nonEmpty                     name:     String,
  @email                        email:    String,
  @masked                       password: Option[String]
)

val storedRaw: RawObject = Map(
  "id"       -> 42L,
  "name"     -> "Alice",
  "email"    -> "alice@example.com",
  "password" -> "s3cr3t"
)

val safeRaw: RawObject = userContract.sanitize(storedRaw)
// Result:
// Map(
//   "name"     -> "Alice",
//   "email"    -> "alice@example.com",
//   "password" -> "***"          ← masked
//   // "id" is gone              ← internal
// )
```

**Custom mask string:**

```scala
@masked("<redacted>") ssn: Option[String]
```

<details>
<summary>Java</summary>

```java
Map<String, Object> safeRaw = userContract.sanitize(storedRaw);
// id is gone, password is "***"
```

</details>

<details>
<summary>Kotlin</summary>

```kotlin
val safeRaw: Map<String, Any> = userContract.sanitize(storedRaw)
```

</details>

### JSON output from sanitize

`sanitizeJson` is a single-call shortcut that sanitizes and serialises to a JSON string in one step — no third-party JSON library required.

```scala
// Compact (single-line) JSON — id stripped, password masked
val json: String = userContract.sanitizeJson(storedRaw)
// → {"email":"alice@example.com","name":"Alice","password":"***"}

// Pretty-printed with 2-space indent
val pretty: String = userContract.sanitizeJson(storedRaw, indent = 2)
// → {
//     "email": "alice@example.com",
//     "name": "Alice",
//     "password": "***"
//   }
```

<details>
<summary>Java</summary>

```java
String json   = userContract.sanitizeJson(storedRaw);
String pretty = userContract.sanitizeJson(storedRaw, 2);
```

</details>

<details>
<summary>Kotlin</summary>

```kotlin
val json   = userContract.sanitizeJson(storedRaw)
val pretty = userContract.sanitizeJson(storedRaw, 2)
```

</details>

---

## toRaw — serialize back to a raw map

`toRaw` converts an already-constructed `T` back to a `RawObject`. It is the inverse of `validate`. Null fields and `None` options are omitted.

```scala
val user = User(id = 1L, name = "Alice", email = "alice@example.com", age = 30)
val raw: RawObject = userContract.toRaw(user)
// Map("id" -> 1, "name" -> "Alice", "email" -> "alice@example.com", "age" -> 30)
```

For `@include`-flattened types the result is in the flat wire format (see [@include — inline nested types](#include--inline-nested-types)).

<details>
<summary>Java</summary>

```java
Map<String, Object> raw = userContract.toRaw(user);
```

</details>

### JSON output from toRaw

`toJson` serialises a constructed `T` directly to JSON without going through a raw map first.

```scala
val user = User(id = 1L, name = "Alice", email = Some("alice@example.com"), age = 30, password = None)

// Compact
val json: String = userContract.toJson(user)
// → {"age":30,"email":"alice@example.com","id":1,"name":"Alice"}

// Pretty-printed
val pretty: String = userContract.toJson(user, indent = 2)
```

Note that `toJson` includes **all** fields including those marked `@internal` or `@masked` — it is the raw serialisation of `T` as stored. To produce output safe for external consumers use `sanitizeJson` instead.

<details>
<summary>Java</summary>

```java
String json   = userContract.toJson(user);
String pretty = userContract.toJson(user, 2);
```

</details>

<details>
<summary>Kotlin</summary>

```kotlin
val json   = userContract.toJson(user)
val pretty = userContract.toJson(user, 2)
```

</details>

### RawJson — standalone serialiser

`RawJson` is the zero-dependency JSON serialiser used internally by `toJson` and `sanitizeJson`. You can call it directly when you already have a `RawObject` from some other source.

```scala
import io.concentric.RawJson

val raw: RawObject = Map("name" -> "Alice", "scores" -> List(1, 2, 3), "active" -> true)
RawJson.stringify(raw)           // → {"active":true,"name":"Alice","scores":[1,2,3]}
RawJson.stringify(raw, indent=2) // pretty-printed
```

<details>
<summary>Type handling summary</summary>

| Value type | JSON output |
|---|---|
| `String` | `"escaped string"` |
| `Boolean` | `true` / `false` |
| `Int`, `Long`, `BigInt` | integer literal |
| `Double`, `Float`, `BigDecimal` | decimal (no trailing zeros); `NaN`/`Infinity` → `null` |
| `None` / `null` (inside object) | field omitted |
| `None` / `null` (inside array) | `null` |
| `Some(v)` | unwrapped as `v` |
| `java.util.Optional` | empty → `null`; present → inner value |
| `Map[String, Any]` | JSON object (keys sorted alphabetically) |
| `Seq[_]` / `Array[_]` / `java.util.Collection` | JSON array |
| Anything else | `toString`, serialised as a string |

</details>

Object keys are always sorted alphabetically for deterministic output. When you already have circe, Jackson, or play-json on the classpath you can convert the `RawObject` through your existing library instead — `RawJson` is provided as a self-contained default that adds no dependencies.

---

## Patch — type-safe partial updates

`Patch[T]` is a compile-time-safe builder for constructing partial updates. Field names are extracted from selector lambdas at compile time, so a misspelling is a compile error.

### Building and applying a patch

```scala
val patch: Patch[User] = Patch.empty[User]
  .set(_.name, "Alicia")       // replace name with a fixed value
  .set(_.age, 31)              // replace age
  .modify(_.age, _ + 1)        // alternatively: increment age by 1 from current
  .unset(_.password)           // clear an optional field

// Apply against the current stored state
val result: Either[ContractViolations, User] =
  userContract.applyPatch(currentRaw, patch)
```

`Patch.modify` receives the current raw value from `currentRaw`. If the field is absent in `currentRaw` the modifier is silently skipped. When both `.set` and `.modify` target the same field, `.set` wins.

### Interop with raw maps

```scala
// Build from a raw map (useful when the patch comes from a JSON body)
val rawPatch: RawObject = Map("name" -> "Alicia", "age" -> 31)
val typed = Patch.fromRaw[User](rawPatch)
userContract.applyPatch(currentRaw, typed)
```

<details>
<summary>Java / Kotlin</summary>

Java and Kotlin callers can use `validatePatch` with a plain `Map`, or use the minimal `JvmPatch` builder when a fluent raw-map wrapper is more convenient. Unlike Scala `Patch[T]`, `JvmPatch` uses string field names and performs no compile-time field checking.

```java
Map<String, Object> patch = Map.of("name", "Alicia", "age", 31);
userContract.validatePatch(currentRaw, patch);

JvmPatch patch2 = JvmPatch.empty()
    .set("name", "Alicia")
    .set("age", 31);
userContract.validatePatch(currentRaw, patch2);
```

```kotlin
val patch = JvmPatch.empty()
    .set("name", "Alicia")
    .set("age", 31)
userContract.validatePatch(currentRaw, patch)
```

</details>

---

## Filter — type-safe query expressions

`Filter[T]` builds composable filter expressions over a contract type. Expressions can be evaluated in-memory or translated to MongoDB-compatible query documents.

```scala
val f: Filter[User] = userContract.filter   // or Filter[User]

// Build an expression
val expr: FilterExpr[User] =
  f.field(_.age).gte(18) &&
  f.field(_.name).startsWith("A") &&
  f.field(_.email).exists

// In-memory predicate
val items: List[RawObject] = expr(rawItems)    // filter a collection
val matches: Boolean       = expr.test(rawMap) // test a single map

// MongoDB query document
val mongoQuery: RawObject = expr.toMongoQuery
// Map("age" -> Map("$gte" -> 18),
//     "name" -> Map("$regex" -> "^A"),
//     "email" -> Map("$exists" -> true))
```

### All filter operators

**General (all types):**

| Method | MongoDB equivalent | Description |
|---|---|---|
| `.exists` | `{$exists: true}` | Field is present and non-null. |
| `.notExists` | `{$exists: false}` | Field is absent or null. |
| `.is(v)` / `.===(v)` | `{field: v}` | Equality. |
| `.isNot(v)` / `.!==(v)` | `{field: {$ne: v}}` | Inequality. |
| `.gt(v)` / `.>(v)` | `{field: {$gt: v}}` | Greater than. |
| `.gte(v)` / `.>=(v)` | `{field: {$gte: v}}` | Greater than or equal. |
| `.lt(v)` / `.<(v)` | `{field: {$lt: v}}` | Less than. |
| `.lte(v)` / `.<=(v)` | `{field: {$lte: v}}` | Less than or equal. |
| `.in(Seq(…))` | `{field: {$in: [...]}}` | Value in set. |
| `.notIn(Seq(…))` | `{field: {$nin: [...]}}` | Value not in set. |

**String fields only:**

| Method | MongoDB equivalent | Description |
|---|---|---|
| `.contains(s)` | `{$regex: "<s>"}` | Substring match. |
| `.startsWith(s)` | `{$regex: "^<s>"}` | Prefix match. |
| `.matches(r)` | `{$regex: "^(?:r)$"}` | Full anchored regex match. |

**Optional fields:**

| Method | Description |
|---|---|
| `.isSome(v)` | Present and equals `v`. |
| `.isNone` / `.isEmpty` | Absent or null. |
| `.isDefined` | Present and non-null. |

**Logical combinators:**

```scala
val expr1 = f.field(_.age).gte(18) && f.field(_.age).lt(65)  // AND
val expr2 = f.field(_.name).is("Alice") || f.field(_.name).is("Bob")  // OR
val expr3 = !(f.field(_.email).exists)  // NOT
```

---

## View — composable output transforms

`View[T]` is a lightweight pipeline of transforms applied to a `RawObject`. Unlike `sanitize` (which is annotation-driven and always strips `@internal` / masks `@masked`), `View` lets you define arbitrary per-endpoint output shapes at the call site.

```scala
// Public profile view: omit id and password, mask email partially, add displayName
val publicView: View[User] = View[User]
  .omit(_.id)
  .omit(_.password)
  .mask(_.email, "***")
  .compute("displayName", raw =>
    raw.getOrElse("name", "unknown").toString.toUpperCase
  )

val publicRaw: RawObject = publicView(storedRaw)
// id and password are gone, email is "***", displayName is added
```

Transforms are applied **in order**. A `compute` that references a field already omitted by an earlier `omit` will see the key as absent.

```scala
// Chain multiple views
val adminView: View[User] = View[User].omit(_.password)  // admins don't see passwords

// Combine with sanitize for defence-in-depth:
val sanitized = userContract.sanitize(storedRaw)    // strips @internal, masks @masked
val shaped    = publicView(sanitized)               // further shapes for the response
```

### Nested updates — Monocle

For deeply nested immutable updates on validated `T` values, plain `copy()` works well for one or two levels:

```scala
user.copy(address = user.address.copy(city = "New York"))
```

For deeper nesting, concentric types are plain case classes so [Monocle](https://www.optics.dev/Monocle/) works on them out of the box — no extra wiring required:

```scala
// build.sbt
libraryDependencies += "dev.optics" %% "monocle-core" % "<version>"

// Usage
import monocle.syntax.all.*

val updated = company.focus(_.ceo.address.city).replace("New York")
```

concentric does not provide its own lens API — Monocle covers this use case completely since all contract types are standard case classes.

---

## Optional / nullable fields

### Scala: `Option[A]`

Declare an optional field with `Option[A]`. When absent from input, the field gets `None` without any `ExpectedMissing` violation. Constraints on the field are only applied when the value is present (non-None).

```scala
@contract
case class Profile(
  @nonEmpty             username: String,
                        bio:      Option[String] = None,    // optional, no constraints
  @min(0)               score:    Option[Int]    = None     // optional, @min only if present
)

// bio absent → fine, no violation
Profile.validate(Map("username" -> "alice"))
// → Right(Profile("alice", None, None))

// bio present and valid:
Profile.validate(Map("username" -> "alice", "bio" -> "Hello"))
// → Right(Profile("alice", Some("Hello"), None))

// score present but invalid:
Profile.validate(Map("username" -> "alice", "score" -> -1))
// → Left: ConstraintFailed("min") at "score"
```

<details>
<summary>Java</summary>

```java
@contract
public record Profile(
    @nonEmpty               String              username,
                            Optional<String>    bio,
    @min(0)                 Optional<Integer>   score
) {}

JvmContract<Profile> profileContract = JvmContract.ofRecord(Profile.class);

// bio absent:
profileContract.validate(Map.of("username", "alice"));

// bio present:
profileContract.validate(Map.of("username", "alice", "bio", "Hello"));

// score invalid:
profileContract.validate(Map.of("username", "alice", "score", Optional.of(-1)));
// → error: CONSTRAINT(min) at "score"
```

</details>

<details>
<summary>Kotlin</summary>

```kotlin
@contract
data class Profile(
    @field:nonEmpty         val username: String,
                            val bio:      java.util.Optional<String> = java.util.Optional.empty(),
    @field:min(0)           val score:    java.util.Optional<Int> = java.util.Optional.empty()
)
```

`JvmContract` treats `java.util.Optional<T>` as optional on the JVM path. For real Kotlin data classes derived through `JvmContract.ofPrimary`, the runtime now also uses Kotlin reflection to read constructor defaults and Kotlin nullability, so omitted `field: Type? = null` parameters are treated as optional. For plain JVM classes without Kotlin metadata, supported runtime `@Nullable` annotations (`org.jetbrains.annotations.Nullable`, `javax.annotation.Nullable`, `jakarta.annotation.Nullable`, and common Android variants) are also treated as optional. Plain nullable references without Kotlin metadata or a runtime nullable annotation are still treated as required.

</details>

---

## ContractValidator — cross-field rules

For business rules that span multiple fields — things that cannot be expressed with per-field annotations — implement `ContractValidator[T]` and attach it with `@validateContract`.

The validator runs **after** all field-level checks pass. If any field-level violation exists, contract validators are not called.

```scala
import io.concentric.ContractValidator

class CheckInBeforeCheckOut extends ContractValidator[Booking]:
  def validate(b: Booking): List[String] =
    if b.checkIn >= b.checkOut
    then List("checkIn must be before checkOut")
    else Nil

class PositiveDuration extends ContractValidator[Booking]:
  def validate(b: Booking): List[String] =
    val days = (b.checkOut - b.checkIn) / 86400000L
    if days > 365
    then List("Booking cannot exceed 365 days")
    else Nil

@contract
@validateContract(Array(classOf[CheckInBeforeCheckOut], classOf[PositiveDuration]))
case class Booking(
  @nonEmpty checkIn:  Long,
  @nonEmpty checkOut: Long,
  @nonEmpty guestId:  String
) derives Contract

val bookingContract = summon[Contract[Booking]]

// All validators run after field checks:
bookingContract.validate(Map(
  "checkIn"  -> 1700000000000L,
  "checkOut" -> 1699000000000L,  // before checkIn!
  "guestId"  -> "guest-123"
))
// → Left: ContractRule("validateContract") — "checkIn must be before checkOut"
```

Violations from `ContractValidator` carry:
- `path`: empty (`FieldPath(Nil)`) — not attributable to a single field
- `code`: `ViolationCode.ContractRule("validateContract")`
- `message`: the string returned from `validate`

`JvmContract` enforces `@validateContract` too. One caveat: `ContractValidator[T]` currently returns a Scala `List[String]`, so authoring the validator class itself is still more ergonomic in Scala than in plain Java.

<details>
<summary>Java</summary>

```java
@contract
@validateContract({CheckInBeforeCheckOut.class})
public record Booking(
    @nonEmpty long   checkIn,
    @nonEmpty long   checkOut,
    @nonEmpty String guestId
) {}
```

</details>

<details>
<summary>Kotlin</summary>

```kotlin
@contract
@validateContract([CheckInBeforeCheckOut::class])
data class Booking(
    @field:nonEmpty val checkIn:  Long,
    @field:nonEmpty val checkOut: Long,
    @field:nonEmpty val guestId:  String
)
```

</details>

---

## Custom field validators

Implement `FieldValidator[A]` to create reusable field-level validation logic beyond what annotations provide.

```scala
import io.concentric.annotations.{FieldValidator, validateWith}

class E164PhoneValidator extends FieldValidator[String]:
  def validate(value: String): List[String] =
    if value.matches("\\+[1-9]\\d{6,14}")
    then Nil
    else List(s"'$value' is not a valid E.164 phone number")

class NoSpacesValidator extends FieldValidator[String]:
  def validate(value: String): List[String] =
    if value.contains(" ")
    then List("Value must not contain spaces")
    else Nil

@contract
case class Contact(
  @validateWith(Array(classOf[E164PhoneValidator], classOf[NoSpacesValidator]))
  phone: String,
  name:  String
)
```

Multiple validators are all run; their failures are accumulated together. `JvmContract` enforces `@validateWith` on Java and Kotlin models as well.

<details>
<summary>Java</summary>

```java
import io.concentric.annotations.*;
import java.util.List;

public class E164PhoneValidator implements FieldValidator<String> {
    @Override
    public List<String> validate(String value) {
        return value.matches("\\+[1-9]\\d{6,14}")
            ? List.of()
            : List.of("'" + value + "' is not a valid E.164 phone number");
    }
}

@contract
public record Contact(
    @validateWith({E164PhoneValidator.class}) String phone,
    String name
) {}
```

</details>

<details>
<summary>Kotlin</summary>

```kotlin
import io.concentric.annotations.FieldValidator
import io.concentric.annotations.contract
import io.concentric.annotations.validateWith

class E164PhoneValidator : FieldValidator<String> {
    override fun validate(value: String): List<String> =
        if (value.matches(Regex("\\+[1-9]\\d{6,14}")))
            emptyList()
        else
            listOf("'$value' is not a valid E.164 phone number")
}

@contract
data class Contact(
    @field:validateWith([E164PhoneValidator::class])
    val phone: String,
    val name: String
)
```

</details>

---

## @include — inline nested types

`@include` flattens all fields of a nested contract type into the parent's wire format. The Scala model retains the nested structure; the wire format is flat.

```scala
@contract
case class Timestamps(
  @immutable createdAt: Long,
             updatedAt: Long
) derives Contract

@contract
case class Article(
  @immutable @internal    id:         Long,
  @nonEmpty               title:      String,
  @nonEmpty               content:    String,
  @include                timestamps: Timestamps
) derives Contract

val timestampsContract = summon[Contract[Timestamps]]
val articleContract = summon[Contract[Article]]

// Wire format (flat — no "timestamps" nesting):
val raw: RawObject = Map(
  "id"        -> 1L,
  "title"     -> "Hello World",
  "content"   -> "...",
  "createdAt" -> 1700000000000L,
  "updatedAt" -> 1700001000000L
)

articleContract.validate(raw)
// → Right(Article(1L, "Hello World", "...", Timestamps(1700000000000L, 1700001000000L)))

// toRaw produces the same flat format:
articleContract.toRaw(article)
// → Map("id"->1, "title"->"Hello World", "content"->"...", "createdAt"->..., "updatedAt"->...)
```

Constraints, `@immutable`, and all other annotations on inner fields are fully respected when the parent is validated or patched.

<details>
<summary>Java</summary>

```java
import io.concentric.annotations.*;
import io.concentric.JvmContract;
import java.util.Map;

@contract
public record Timestamps(
    @immutable Long createdAt,
               Long updatedAt
) {}

@contract
public record Article(
    @immutable @internal Long id,
    @nonEmpty            String title,
    @nonEmpty            String content,
    @include             Timestamps timestamps
) {}

JvmContract<Article> articleContract = JvmContract.ofRecord(Article.class);

Map<String, Object> raw = Map.ofEntries(
    Map.entry("id", 1L),
    Map.entry("title", "Hello World"),
    Map.entry("content", "..."),
    Map.entry("createdAt", 1700000000000L),
    Map.entry("updatedAt", 1700001000000L)
);

ValidationResult<Article> result = articleContract.validate(raw);
Map<String, Object> out = articleContract.toRaw(result.getValue().get());
// out is flat: id, title, content, createdAt, updatedAt
```

</details>

<details>
<summary>Kotlin</summary>

```kotlin
import io.concentric.JvmContract
import io.concentric.annotations.*

@contract
data class Timestamps(
    @field:immutable val createdAt: Long,
    val updatedAt: Long
)

@contract
data class Article(
    @field:immutable @field:internal val id: Long,
    @field:nonEmpty val title: String,
    @field:nonEmpty val content: String,
    @field:include val timestamps: Timestamps
)

val articleContract: JvmContract<Article> = JvmContract.ofPrimary(Article::class.java)

val raw = mapOf<String, Any>(
    "id" to 1L,
    "title" to "Hello World",
    "content" to "...",
    "createdAt" to 1700000000000L,
    "updatedAt" to 1700001000000L
)

val result = articleContract.validate(raw)
val out = articleContract.toRaw(result.value.get())
// out is flat: id, title, content, createdAt, updatedAt
```

</details>

As on the Scala side, only one level of `@include` flattening is supported; recursive flattening is not performed.

---

## @discriminator — tagged-union wire format

`@discriminator` configures an `Either[A, B]` field to use a tagged-union wire format keyed by a discriminator **inside the field’s object** (the field itself remains a nested map). `validate`/`toRaw` both follow this tagged format. Without the annotation, the default `Either` envelope is used: `{"left": ...}` / `{"right": ...}`.

```scala
@contract
case class Cat(name: String, indoor: Boolean)

@contract
case class Dog(name: String, breed: String)

@contract
case class PetHolder(
  owner: String,
  @discriminator("type", left = "cat", right = "dog")
  pet:   Either[Cat, Dog]
)

// Wire format for Left(Cat("Whiskers", true)):
Map(
  "owner" -> "Alice",
  "pet"   -> Map("type" -> "cat", "name" -> "Whiskers", "indoor" -> true)
)

// Wire format for Right(Dog("Rex", "Labrador")):
Map(
  "owner" -> "Bob",
  "pet"   -> Map("type" -> "dog", "name" -> "Rex", "breed" -> "Labrador")
)
```

Without `@discriminator` the default `Either` wire format is:

```
{"owner": "Alice", "pet": {"left": {"name": "Whiskers", "indoor": true}}}
```

`@discriminator` is currently Scala-only. The JVM API does not yet expose a first-class tagged-union / sum-type model comparable to Scala’s `Either[A, B]` derivation, so there is no Java/Kotlin example yet.

---

## @decodable and @extract — structured string types

`@decodable` remains Scala-only in practice today. Runtime tests show that `JvmContract` does not support `@decodable` wrapper fields yet, because JVM reflection does not currently derive wrapper `RawDecoder`s from the annotation. Field-level `@extract`, however, is enforced on `JvmContract`.

### @decodable — value wrapper types

Mark a single-field case class with `@decodable` and derive its `RawDecoder` to use it as a contract field type:

```scala
@decodable
case class Email(value: String)
given RawDecoder[Email] = RawDecoder.derived[Email]

@decodable
case class Age(value: Int)
given RawDecoder[Age] = RawDecoder.derived[Age]

@contract
case class User(
  @email contactEmail: Email,  // @email constraint still applies
  name:  String,
  age:   Age
) derives Contract
```

**Why use it?** Without `@decodable`, each wrapper would need a manual decoder:

```scala
case class ProductCode(value: String)
given RawDecoder[ProductCode] with
  def decode(raw: Any): Option[ProductCode] =
    RawDecoder[String].decode(raw).map(ProductCode.apply)
```

`@decodable` + `RawDecoder.derived[...]` generates that boilerplate and preserves the inner field’s coercions (`Long`→`Int`, `String`→`BigDecimal`, etc.), keeping all your newtypes consistent.

### @extract — structured string parser

`@extract` on a `@decodable` class turns it into a regex-based parser. Capture groups map to constructor fields:

```scala
// Three capture groups → three constructor fields
@decodable
@extract("(\\d{4})-(\\d{2})-(\\d{2})")
case class IsoDate(year: Int, month: Int, day: Int)
given RawDecoder[IsoDate] = RawDecoder.derived[IsoDate]

// Validate-only (0 capture groups → single String field)
@decodable
@extract("[A-Z]{2}-\\d{4}")
case class ProductCode(value: String)
given RawDecoder[ProductCode] = RawDecoder.derived[ProductCode]

@contract
case class Shipment(
  date:        IsoDate,
  productCode: ProductCode,
  quantity:    Int
)

Shipment.validate(Map(
  "date"        -> "2024-03-15",     // → IsoDate(2024, 3, 15)
  "productCode" -> "AB-1234",        // → ProductCode("AB-1234")
  "quantity"    -> 10
))
```

The number of capture groups must exactly match the number of constructor fields (for N-group case). A mismatch is a **compile error**.

### @extract on a contract field

`@extract` can also be placed directly on a `String` field as a pattern constraint (similar to `@pattern`):

```scala
@contract
case class Event(
  @extract("\\d{4}-\\d{2}-\\d{2}") date: String
)
// Produces ConstraintFailed("extract") if the pattern doesn't match
```

Field-level `@extract` is enforced by `JvmContract` too.

---

## Open contracts

By default all contracts are **closed** — any unknown field in the input produces an `UNKNOWN_FIELD` violation.

On the Scala side, if you want unknown fields to remain first-class after validation, derive an `OpenContract` instead of a closed `Contract`:

```scala
@contract
case class Metadata(
  @nonEmpty key:   String,
  @nonEmpty value: String
) derives OpenContract

val metadataContract = OpenContract[Metadata]

val raw = Map(
  "key"     -> "theme",
  "value"   -> "dark",
  "source"  -> "ui-settings"  // unknown — silently accepted
)

metadataContract.validate(raw)
// → Right((Metadata("theme", "dark"), Map("source" -> "ui-settings")))

// Match on the typed declared value and the extra fields together:
metadataContract.validate(raw) match
  case Right((Metadata("theme", _), extras))
      if extras.get("source").contains("ui-settings") =>
    println("theme from UI settings")

  case Right((metadata, extras)) =>
    println(s"known = $metadata, extras = $extras")

  case Left(errs) =>
    println(errs)
```

`OpenContract[T]` keeps the declared case class value and the undeclared extras together without forcing the extras into fake case-class fields.

<details>
<summary>Java</summary>

```java
import io.concentric.annotations.*;
import io.concentric.JvmOpenContract;

@contract
public record Metadata(
    @nonEmpty String key,
    @nonEmpty String value
) {}

JvmOpenContract<Metadata> metadataContract = JvmOpenContract.ofRecord(Metadata.class);

Map<String, Object> raw = Map.of(
    "key", "theme",
    "value", "dark",
    "source", "ui-settings"
);

OpenValidationResult<Metadata> result = metadataContract.validate(raw);
// valid → result.getValue().get() is Metadata("theme", "dark")
//         result.getExtras() is {"source": "ui-settings"}
```

`JvmOpenContract.ofRecord` always preserves undeclared fields as extras. The older `@contract(open = true)` JVM path remains supported for compatibility, but `JvmOpenContract` is now the preferred API.

</details>

<details>
<summary>Kotlin</summary>

```kotlin
import io.concentric.annotations.*
import io.concentric.JvmOpenContract

@contract
data class Metadata(
    @field:nonEmpty val key: String,
    @field:nonEmpty val value: String
)

val metadataContract: JvmOpenContract<Metadata> = JvmOpenContract.ofPrimary(Metadata::class.java)

val raw = mapOf<String, Any>(
    "key" to "theme",
    "value" to "dark",
    "source" to "ui-settings"
)

val result = metadataContract.validate(raw)
// valid -> result.value.get() is Metadata("theme", "dark")
//          result.extras["source"] == "ui-settings"
```

`JvmOpenContract.ofPrimary` always preserves undeclared fields as extras. The older `@contract(open = true)` JVM path remains supported for compatibility, but `JvmOpenContract` is now the preferred API.

</details>

---

## Aspects — structural variants of a contract

An **aspect** is a case class that declares a subset (or variation) of an existing contract's fields. Annotate it with `@aspectOf[Source]` and `derives Contract` — the macro inherits constraint annotations from the matching fields in the source and enforces the relationship at compile time.

The primary use case is typed PATCH endpoints: the aspect declares exactly which fields a caller is allowed to change, with all validation rules carried over automatically from the source contract.

### Declaring an aspect

```scala
@contract
case class User(
  @internal @immutable val id:    Long,
  @email               val email: String,
  @nonEmpty @maxLength(100) val name: String,
  @reserved            val role:  Option[String] = None,
  @masked              val token: Option[String] = None
) derives Contract

// PATCH variant — callers can change email and name only.
// @email, @nonEmpty, @maxLength(100) are inherited from User.
// id, role, token are excluded by omission.
@aspectOf[User]
case class UserPatch(
  val email: Option[String] = None,
  val name:  Option[String] = None
) derives Contract
```

### What is and isn't inherited

**Constraint annotations** (`@email`, `@nonEmpty`, `@min`, `@max`, `@maxLength`, `@pattern`, etc.) are inherited from the matching source field. An annotation declared on the aspect field overrides the corresponding one from the source.

**Policy annotations** (`@reserved`, `@internal`, `@immutable`, `@masked`) are **not** inherited. The aspect author decides access rules from scratch. This lets you write a privileged variant that accepts a field the base contract marks as `@reserved`:

```scala
// Admin variant — role is @reserved in User but freely accepted here.
@aspectOf[User]
case class AdminPatch(
  val email: Option[String] = None,
  val name:  Option[String] = None,
  val role:  Option[String] = None   // @reserved NOT inherited — intentional
) derives Contract
```

### New fields not present in the source

An aspect can declare fields that do not exist in the source at all. They use only their own annotations:

```scala
// Registration form — confirmPassword has no match in User; @nonEmpty is its own.
@aspectOf[User]
case class UserRegistration(
  val email:                       Option[String] = None,  // @email inherited
  val name:                        Option[String] = None,  // @nonEmpty/@maxLength(100) inherited
  @nonEmpty val confirmPassword:   Option[String] = None   // new — not in User
) derives Contract
```

### Applying an aspect as a typed patch

`Contract[T].validatePatch` has a typed overload that accepts an aspect value directly. Both the aspect contract and the compile-time proof that `A` is an `@aspectOf[T]` are resolved implicitly:

```scala
val userContract  = summon[Contract[User]]
val patchContract = summon[Contract[UserPatch]]

def handlePatch(currentRaw: RawObject, requestBody: RawObject) =
  for
    patch   <- patchContract.validate(requestBody)   // validate what the caller sent
    updated <- userContract.validatePatch(currentRaw, patch)  // apply against stored state
  yield updated
```

The `Contract[UserPatch]` and `IsAspectOf[User, UserPatch]` are both found implicitly. Passing a type that does not carry `@aspectOf[User]` is a **compile error**:

```scala
// Compile error — AspectOrder is not an @aspectOf[User]
userContract.validatePatch(currentRaw, someOrderPatch)
```

Unknown fields in the request body are **always rejected** by the aspect regardless of the source contract's openness — aspects are closed validators by default.

### Aspect of a closed base contract

When the source derives plain `Contract`, the aspect inherits constraints normally. Fields sent in a request that are not declared in the aspect are rejected:

```scala
@contract
case class Order(
  @nonEmpty val ref:    String,
  @min(1)   val qty:    Int,
  @max(999) val weight: Double
) derives Contract

// Only ref and qty can be patched; weight is excluded.
// @nonEmpty inherited on ref, @min(1) inherited on qty.
@aspectOf[Order]
case class OrderPatch(
  val ref: Option[String] = None,
  val qty: Option[Int]    = None
) derives Contract

val orderPatchContract = summon[Contract[OrderPatch]]

orderPatchContract.validate(Map("qty" -> 5))
// → Right(OrderPatch(None, Some(5)))

orderPatchContract.validate(Map("qty" -> 0))
// → Left: ConstraintFailed("min") at "qty"   — @min(1) inherited

orderPatchContract.validate(Map("qty" -> 5, "weight" -> 1.2))
// → Left: UnknownField at "weight"            — aspect is closed
```

### Aspect of an open base contract

When the source derives `OpenContract` — meaning it accepts arbitrary extra fields alongside its declared ones — the aspect still validates its own fields independently and rejects anything undeclared in the aspect itself:

```scala
case class Product(
  @nonEmpty val sku:   String,
  @min(0)   val price: Double
) derives OpenContract   // source accepts unknown keys

@aspectOf[Product]
case class ProductPatch(
  val sku:   Option[String] = None,  // @nonEmpty inherited
  val price: Option[Double] = None   // @min(0) inherited
) derives Contract

val productPatchContract = summon[Contract[ProductPatch]]

productPatchContract.validate(Map("sku" -> "ABC-123", "price" -> 9.99))
// → Right(ProductPatch(Some("ABC-123"), Some(9.99)))

productPatchContract.validate(Map("sku" -> ""))
// → Left: ConstraintFailed("nonEmpty")        — constraint still inherited

productPatchContract.validate(Map("sku" -> "ABC", "extra" -> "x"))
// → Left: UnknownField at "extra"             — aspect is closed even though source is open
```

The source's openness is irrelevant to the aspect's validation behaviour. The source `OpenContract` describes what can be *stored*; the aspect describes what a *caller is allowed to change*. Those are separate concerns.

<details>
<summary>Java</summary>

Annotate the aspect record with `@aspectOf(Source.class)` and create its contract with `JvmContract.ofAspect`. Constraint annotations are inherited from the source record; policy annotations (`@immutable`, `@internal`, `@reserved`, `@masked`) are not.

```java
import io.concentric.annotations.*;
import io.concentric.annotations.aspectOf;   // explicit import avoids Scala name clash
import io.concentric.JvmContract;
import io.concentric.ValidationResult;
import java.util.Optional;

@contract
public record User(
    @immutable @internal            Long            id,
    @nonEmpty  @maxLength(100)      String          name,
    @email                          String          email,
    @reserved                       Optional<String> role
) {}

// PATCH variant — name and email only; @nonEmpty/@maxLength(100)/@email inherited.
// id is excluded by omission; role is excluded by omission (@reserved not inherited).
@aspectOf(User.class)
public record UserPatch(
    Optional<String> name,
    Optional<String> email
) {}

// Admin variant — role is @reserved in User but freely accepted here.
@aspectOf(User.class)
public record AdminPatch(
    Optional<String> name,
    Optional<String> email,
    Optional<String> role    // new field — not subject to @reserved
) {}
```

```java
static final JvmContract<User>      userContract       = JvmContract.ofRecord(User.class);
static final JvmContract<UserPatch> patchContract      = JvmContract.ofAspect(UserPatch.class, User.class);
static final JvmContract<AdminPatch> adminPatchContract = JvmContract.ofAspect(AdminPatch.class, User.class);
```

`ofAspect` validates at construction time that `@aspectOf(User.class)` is present and that the source matches — it throws `IllegalArgumentException` immediately if the annotation is absent or points to a different class.

**Typed patch handler:**

```java
public ValidationResult<User> handlePatch(
    Map<String, Object> currentRaw,
    Map<String, Object> requestBody
) {
    // Step 1 — validate the incoming patch payload:
    ValidationResult<UserPatch> patchResult = patchContract.validate(requestBody);

    // Step 2 — apply against the stored object.
    // If patchResult is invalid its violations are propagated directly.
    return userContract.validatePatch(currentRaw, patchResult, patchContract);
}
```

`Optional.empty()` fields in `UserPatch` are treated as "leave unchanged". `Optional.of(v)` fields replace the corresponding value in `currentRaw`. The source contract's constraints are re-applied to the merged result.

**Constraint override:**

Redeclare an annotation on the aspect field to tighten (or relax) the inherited rule:

```java
@aspectOf(User.class)
public record UserPatchStrict(
    @maxLength(50) Optional<String> name,   // tighter than User's @maxLength(100)
    Optional<String>                email   // @email still inherited
) {}
```

**Aspect of an open contract:**

```java
@contract(open = true)   // or use JvmOpenContract on the source side
public record Config(
    @nonEmpty String key,
    String value
) {}

@aspectOf(Config.class)
public record ConfigPatch(
    Optional<String> key,    // @nonEmpty inherited
    Optional<String> value
) {}

JvmContract<ConfigPatch> configPatchContract = JvmContract.ofAspect(ConfigPatch.class, Config.class);

// Aspect is always closed — unknown fields rejected even though source is open:
configPatchContract.validate(Map.of("key", "k", "extra", "x"));
// → invalid: UNKNOWN at "extra"
```

For Kotlin data class aspects use `JvmContract.ofAspectPrimary` instead of `ofAspect`.

</details>

<details>
<summary>Kotlin</summary>

Annotate the data class with `@aspectOf(Source::class.java)` and create its contract with `JvmContract.ofAspectPrimary`. The annotation placement rules are the same as for regular `@contract` data classes — use `@field:` to route annotations to the JVM backing field, though `JvmContractDeriver` checks both the field and the constructor parameter.

```kotlin
import io.concentric.annotations.*
import io.concentric.annotations.aspectOf
import io.concentric.JvmContract
import java.util.Optional

@contract
data class User(
    @field:immutable @field:internal val id:    Long,
    @field:nonEmpty  @field:maxLength(100) val name: String,
    @field:email                     val email: String,
    @field:reserved                  val role:  Optional<String> = Optional.empty()
)

// PATCH variant — name and email only; constraints inherited.
@aspectOf(User::class.java)
data class UserPatch(
    val name:  Optional<String> = Optional.empty(),  // @nonEmpty @maxLength(100) inherited
    val email: Optional<String> = Optional.empty()   // @email inherited
)

// Admin variant — role can be freely set (no @reserved inherited).
@aspectOf(User::class.java)
data class AdminPatch(
    val name:  Optional<String> = Optional.empty(),
    val email: Optional<String> = Optional.empty(),
    val role:  Optional<String> = Optional.empty()
)
```

```kotlin
val userContract       = JvmContract.ofPrimary(User::class.java)
val patchContract      = JvmContract.ofAspectPrimary(UserPatch::class.java, User::class.java)
val adminPatchContract = JvmContract.ofAspectPrimary(AdminPatch::class.java, User::class.java)
```

**Typed patch handler:**

```kotlin
fun handlePatch(
    currentRaw:  Map<String, Any>,
    requestBody: Map<String, Any>
): ValidationResult<User> {
    // Step 1 — validate the incoming patch payload:
    val patchResult = patchContract.validate(requestBody)

    // Step 2 — apply against the stored object.
    // If patchResult is invalid its violations are propagated directly.
    return userContract.validatePatch(currentRaw, patchResult, patchContract)
}
```

**Constraint override:**

```kotlin
@aspectOf(User::class.java)
data class UserPatchStrict(
    @field:maxLength(50) val name:  Optional<String> = Optional.empty(), // tighter
    val email: Optional<String> = Optional.empty()                        // @email inherited
)

val strictContract = JvmContract.ofAspectPrimary(UserPatchStrict::class.java, User::class.java)
```

**Aspect of an open contract:**

```kotlin
@contract
data class Config(
    @field:nonEmpty val key:   String,
    val value: String
)

@aspectOf(Config::class.java)
data class ConfigPatch(
    val key:   Optional<String> = Optional.empty(),  // @nonEmpty inherited
    val value: Optional<String> = Optional.empty()
)

val configPatchContract = JvmContract.ofAspectPrimary(ConfigPatch::class.java, Config::class.java)

// Aspect is always closed — unknown fields rejected even though source is open:
configPatchContract.validate(mapOf("key" to "k", "extra" to "x"))
// → invalid: UNKNOWN at "extra"
```

</details>

---

## jsonSchema — derive a JSON Schema document

Every contract can export a JSON Schema (draft-07) document reflecting its annotations.

```scala
val schema: Map[String, Any] = userContract.jsonSchema
// {
//   "$schema":              "http://json-schema.org/draft-07/schema#",
//   "type":                 "object",
//   "properties": {
//     "id":       {"type": "integer", "x-immutable": true, "x-internal": true},
//     "name":     {"type": "string",  "minLength": 1, "maxLength": 100},
//     "email":    {"type": "string",  "format": "email"},
//     "age":      {"type": "integer", "minimum": 0, "maximum": 150},
//     "password": {"type": "string",  "x-masked": "***"}
//   },
//   "required":             ["id", "name", "email"],
//   "additionalProperties": false
// }
```

<details>
<summary>Annotation to JSON Schema mapping</summary>

| Annotation | JSON Schema output |
|---|---|
| `@nonEmpty` | `"minLength": 1` (string) / `"minItems": 1` (array) |
| `@minLength(n)` | `"minLength": n` |
| `@maxLength(n)` | `"maxLength": n` |
| `@min(n)` | `"minimum": n` |
| `@max(n)` | `"maximum": n` |
| `@pattern(r)` | `"pattern": r` |
| `@email` | `"format": "email"` |
| `@url` | `"format": "uri"` |
| `@uuid` | `"format": "uuid"` |
| `@positive` | `"exclusiveMinimum": 0` |
| `@multipleOf(d)` | `"multipleOf": d` |
| `@future` | `"x-future": true` |
| `@past` | `"x-past": true` |
| `@immutable` | `"x-immutable": true` |
| `@internal` | `"x-internal": true` |
| `@reserved` | `"x-reserved": true` |
| `@masked("s")` | `"x-masked": "s"` |

</details>

<details>
<summary>Java</summary>

`JvmContract` exposes two forms. The JSON string is the most convenient — feed it directly to a `/schema` REST endpoint or hand it to Jackson/Gson:

```java
// Compact JSON string
String schema = userContract.jsonSchemaJson();
// → {"$schema":"http://json-schema.org/...","additionalProperties":false,"properties":{...}}

// Pretty-printed (2-space indent)
String pretty = userContract.jsonSchemaJson(2);

// Or as a deeply-converted java.util.Map if you need to inspect the structure
Map<String, Object> schemaMap = userContract.jsonSchema();
Map<?, ?> props = (Map<?, ?>) schemaMap.get("properties");
```

A typical Spring Boot schema endpoint looks like:

```java
@GetMapping("/schema/user")
public ResponseEntity<String> userSchema() {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(userContract.jsonSchemaJson());
}
```

</details>

<details>
<summary>Kotlin</summary>

```kotlin
// Compact JSON string — return directly from a Ktor/Spring route
val schema: String = userContract.jsonSchemaJson()

// Pretty-printed
val pretty: String = userContract.jsonSchemaJson(2)

// As a Map if you need to merge with existing OpenAPI structures
val schemaMap: Map<String, Any> = userContract.jsonSchema()
val properties = schemaMap["properties"] as Map<*, *>
```

Ktor example:

```kotlin
get("/schema/user") {
    call.respondText(
        text        = userContract.jsonSchemaJson(2),
        contentType = ContentType.Application.Json
    )
}
```

</details>

---

## Java and Kotlin interop

### JvmContract[T]

`JvmContract[T]` is the Java/Kotlin API surface. It wraps `ContractImpl` — the same engine as the Scala macro path — so every validation behaviour is identical.

#### Zero-boilerplate factories (recommended)

For Java records (Java 16+) use `JvmContract.ofRecord` — the library discovers the canonical constructor via `getRecordComponents()` and handles all type coercions automatically:

```java
// One line — no manual casts, no field ordering to get wrong:
JvmContract<User> contract = JvmContract.ofRecord(User.class);

// Open contract variant:
JvmOpenContract<Metadata> open = JvmOpenContract.ofRecord(Metadata.class);
```

For Kotlin data classes (and Java POJOs) use `JvmContract.ofPrimary` — it finds the primary non-synthetic constructor, and for real Kotlin classes it also uses Kotlin reflection to honour the true primary constructor, parameter defaults, and nullability:

```kotlin
// Kotlin — one line:
val userContract: JvmContract<User> = JvmContract.ofPrimary(User::class.java)

// Open variant:
val metaContract: JvmOpenContract<Metadata> = JvmOpenContract.ofPrimary(Metadata::class.java)
```

Both factory families build the constructor function **once at startup** using reflection; there is no per-request overhead. `ofPrimary` uses Kotlin reflection automatically when the target class carries Kotlin metadata. They throw `IllegalArgumentException` with a clear message if the class does not meet the requirement (not a record for `ofRecord`; no usable constructor for `ofPrimary`).

Nested JVM contract types are also derived recursively for direct object fields, `Optional<nested>` fields, and `List<nested>` fields. A nested Java record / Kotlin data-class-style model can therefore be declared directly in the parent model without falling back to raw Scala maps, and nested `@reserved`, `@internal`, `@masked`, `@immutable`, and `@validateContract` rules are enforced through `validate`, `validatePatch`, `sanitize`, and `jsonSchema`.

#### Manual constructor (escape hatch)

When you need custom construction logic — default values, post-processing, or compatibility with JVM < 16 — use the explicit `JvmContract.of(...)` form:

```java
JvmContract<User> contract = JvmContract.of(
    User.class,
    fields -> new User(
        (Long)   fields.get("id"),
        (String) fields.get("name"),
        (String) fields.get("email"),
        ((Number) fields.get("age")).intValue(),
        (String) fields.get("password")
    )
);

// Open contract:
JvmOpenContract<Metadata> openContract = JvmOpenContract.of(Metadata.class, constructFn);
```

The `fields` map passed to the lambda has already had all type coercions applied by the validation engine. `Optional` fields are always present (as `Optional.of(value)` or `Optional.empty()`), nested contract-backed fields arrive as typed nested JVM instances, nested `List` fields can be materialized as typed JVM elements, and map/list values are exposed as Java collections rather than Scala collections. Prefer `JvmOpenContract` for open semantics; the legacy `@contract(open = true)` and boolean-open overloads remain available for compatibility.

Current JVM limitations:

- Optionality is inferred from `java.util.Optional<T>`, real Kotlin metadata, or a supported runtime `@Nullable` annotation. Plain Java references and non-Kotlin classes without such metadata are still treated as required.
- Recursive JVM derivation now covers direct nested contract objects, `Optional<nested>`, and `List<nested>`. `Map<String, nested>` and broader generic container support are not yet automatic.
- The dedicated real-Kotlin interop test module in [kotlin-it](/Users/anand.krishnan/example/concentric-zio/kotlin-it) now compiles on Java 25 in this repo. That requires a Kotlin compiler/runtime new enough to understand Java 25 class-library metadata; this build uses Kotlin `2.3.0`.

### ValidationResult[T]

All JvmContract validation methods return `ValidationResult<T>`:

```java
ValidationResult<User> result = contract.validate(raw);

result.isValid()                // boolean
result.getValue()               // Optional<T> — present only when valid
result.getErrors()              // List<JvmViolation> — empty when valid
```

<details>
<summary>JvmContract public API</summary>

| Method | Description |
|---|---|
| `validate(Map)` | Full validation. |
| `validatePatch(Map, Map)` | Patch validation against current state. |
| `validatePatch(Map, JvmPatch)` | Patch validation using the fluent JVM patch builder. |
| `validatePartial(Map)` | Partial validation — returns `List<JvmViolation>`, no missing-field errors. |
| `collectViolations(Map)` | All violations without constructing T. |
| `sanitize(Map)` | Strips `@internal`, masks `@masked`. Returns `Map<String, Object>`. |
| `sanitizeJson(Map)` | Same as `sanitize` but returns a compact JSON string. |
| `sanitizeJson(Map, int)` | Same but pretty-printed with the given indent width. |
| `toRaw(T)` | Serializes T back to a raw map. |
| `toJson(T)` | Serializes T directly to a compact JSON string. |
| `toJson(T, int)` | Same but pretty-printed with the given indent width. |
| `jsonSchemaJson()` | JSON Schema (draft-07) as a compact JSON string. |
| `jsonSchemaJson(int)` | JSON Schema as a pretty-printed JSON string. |
| `jsonSchema()` | JSON Schema as a deeply-converted `Map<String, Object>`. |
| `extraFields(Map)` | Unknown fields from a raw object as a deeply-converted `Map<String, Object>`. |
| `fieldMetas` | The `List<FieldMeta>` for this contract. |

</details>

### JvmPatch

`JvmPatch` is a small Java/Kotlin-friendly builder for raw patch maps:

```java
JvmPatch patch = JvmPatch.empty()
    .set("name", "Alicia")
    .set("age", 31);
```

It exists as a convenience wrapper over `Map<String, Object>`; field names remain strings and are checked only when the patch is validated.

<details>
<summary>Kotlin annotation placement</summary>

Kotlin data classes place annotations differently depending on the use-site target. concentric detects both styles:

**With `@field:` (recommended — annotations on JVM backing field):**

```kotlin
@contract
data class User(
    @field:immutable val id:    Long,
    @field:nonEmpty  val name:  String,
    @field:email     val email: String,
    @field:min(0)    val age:   Int = 0
)
```

**Without `@field:` (annotations on constructor parameter):**

```kotlin
@contract
data class User(
    @immutable val id:    Long,
    @nonEmpty  val name:  String,
    @email     val email: String,
    @min(0)    val age:   Int = 0
)
```

Both styles are fully supported. `@field:` is recommended because it places annotations exactly where `JvmContractDeriver` looks first, and it matches Java field-annotation semantics.

</details>

<details>
<summary>Java record vs POJO</summary>

Java records (Java 16+) and plain POJOs (Java 11+) are both supported:

```java
// Java 16+: record (preferred)
@contract
public record User(@immutable Long id, @nonEmpty String name) {}

// Java 11+: plain class with field annotations
@contract
public class User {
    @immutable
    public final Long id;
    @nonEmpty
    public final String name;
    // constructor...
}
```

On JVM < 16 the record syntax does not compile; use plain classes. The `JvmContractDeriver` automatically detects records and reads their canonical field order from `getRecordComponents()`.

</details>

---

## Core concepts

| Concept | Description |
|---|---|
| `Contract[T]` | The central abstraction. Knows how to validate, sanitize and serialise `T`. |
| `RawObject` | `Map[String, Any]` — the untyped wire format you get from JSON parsers. |
| `Violation` | A single validation failure: path + code + human message. |
| `ContractViolations` | A non-empty collection of violations returned in `Either`. |
| `FieldMeta` | Runtime descriptor for a single field: name, type, decoder, all annotation flags. |
| `ViolationCode` | Scala 3 enum with variants `ExpectedMissing`, `TypeMismatch`, `ConstraintFailed`, `ImmutableField`, `ReservedField`, `UnknownField`, `ContractRule`. |

All contract operations return `Either[ContractViolations, T]` (or plain values for read-only helpers). Lift into your preferred effect as needed.

---

## Validation annotations reference

### Class-level annotations

| Annotation | Usage | Description |
|---|---|---|
| `@contract` | `@contract` | Marks the class as a concentric contract. Legacy `open = true` remains supported for compatibility, but prefer `OpenContract` / `JvmOpenContract` for explicit open semantics. |
| `@validateContract` | `@validateContract(Array(classOf[MyValidator]))` | Attaches cross-field validators that run after all field checks pass. Supported by both Scala `Contract[T]` and `JvmContract`, though authoring the validator class is currently more natural in Scala. |

### Field-level annotations

#### Access control

| Annotation | Description |
|---|---|
| `@immutable` | The field cannot be changed once set. A patch that includes an `@immutable` field produces an `IMMUTABLE` violation. Has no effect during `validate` or `validatePartial`. |
| `@reserved` | The field must not be set by callers. Any input that includes a `@reserved` field produces a `RESERVED` violation. The server sets these values internally (e.g. a server-generated `trackingId`). |
| `@internal` | The field is stripped from `sanitize` output. Useful for fields that should never be sent over the wire (internal flags, system IDs). |
| `@masked` | The field value is replaced with a mask string in `sanitize` output. Default mask is `"***"`. Custom: `@masked("<redacted>")`. |

#### String constraints

| Annotation | Example | Description |
|---|---|---|
| `@nonEmpty` | `@nonEmpty String name` | Rejects blank/empty strings and empty collections. |
| `@minLength(n)` | `@minLength(3) String code` | Minimum character length. |
| `@maxLength(n)` | `@maxLength(100) String bio` | Maximum character length. |
| `@pattern(r)` | `@pattern("[A-Z]{2}-\\d{4}") String sku` | Full regex match (anchored). |
| `@email` | `@email String contact` | Must be a valid e-mail address. |
| `@url` | `@url String homepage` | Must be a valid HTTP/HTTPS URL. |
| `@uuid` | `@uuid String requestId` | Must be a valid UUID string. |

#### Numeric constraints

| Annotation | Example | Description |
|---|---|---|
| `@min(n)` | `@min(0) Int age` | Minimum numeric value (inclusive). |
| `@max(n)` | `@max(150) Int age` | Maximum numeric value (inclusive). |
| `@positive` | `@positive Double price` | Value must be strictly greater than zero. |
| `@multipleOf(d)` | `@multipleOf(5) Int quantity` | Value must be an exact multiple of `d`. |

#### Temporal constraints (epoch-millis)

| Annotation | Description |
|---|---|
| `@future` | The `Long` timestamp must be in the future (`> System.currentTimeMillis()`). |
| `@past` | The `Long` timestamp must be in the past (`< System.currentTimeMillis()`). |

#### Custom validators

| Annotation | Description |
|---|---|
| `@validateWith(Array(classOf[MyValidator]))` | Attaches one or more `FieldValidator[A]` implementations to a field. Supported on both Scala `Contract[T]` and `JvmContract`. |

#### Structural annotations

| Annotation | Description |
|---|---|
| `@include` | Inlines a nested contract type's fields into the parent's flat wire format. |
| `@discriminator(key, left, right)` | Configures a tagged-union wire format for `Either[A, B]` fields. Scala-only today; the JVM API does not yet have a first-class sum-type / tagged-union derivation story. |
| `@decodable` | Marks a single-field wrapper class for automatic `RawDecoder` derivation. Scala-only today; JVM reflection does not yet derive wrapper decoders from `@decodable`. |
| `@extract(regex)` | On a field: regex constraint. On a `@decodable` class: structured string parser with capture groups. Field-level use is supported on both Scala and `JvmContract`; `@decodable` parsing remains Scala-only. |

---

## Violation codes reference

| Code | When raised | Programmatic form |
|---|---|---|
| `MISSING` | A required field was absent from the input. | `ViolationCode.ExpectedMissing` |
| `TYPE_MISMATCH` | The raw value could not be decoded to the declared type. | `ViolationCode.TypeMismatch(expected, actual)` |
| `CONSTRAINT(name)` | A field-level annotation constraint was not satisfied. | `ViolationCode.ConstraintFailed(name)` |
| `IMMUTABLE` | A patch tried to change an `@immutable` field. | `ViolationCode.ImmutableField` |
| `RESERVED` | A caller supplied a `@reserved` field. | `ViolationCode.ReservedField` |
| `UNKNOWN` | An undeclared field appeared in a closed contract. | `ViolationCode.UnknownField` |
| `RULE(name)` | A `@validateContract` cross-field rule failed. | `ViolationCode.ContractRule(name)` |

Constraint names match the annotation name: `"nonEmpty"`, `"min"`, `"max"`, `"minLength"`, `"maxLength"`, `"email"`, `"url"`, `"uuid"`, `"pattern"`, `"extract"`, `"positive"`, `"multipleOf"`, `"future"`, `"past"`, `"validateWith"`, `"validateContract"`.

### Java/Kotlin: JvmViolation

Java and Kotlin callers receive `JvmViolation` objects with flat `String` fields:

```java
String path    = violation.path;    // e.g. "name", "address.city", ""
String code    = violation.code;    // e.g. "MISSING", "CONSTRAINT(email)", "IMMUTABLE"
String message = violation.message; // human-readable
```

---

## RawDecoder — supported field types

`RawDecoder[T]` is a type class that converts a raw `Any` value from the input map to a typed `T`. The following decoders are provided out of the box:

| Type | Widening accepted |
|---|---|
| `String` | — |
| `Boolean` | — |
| `Int` | `Long` → `Int` (when in range) |
| `Long` | `Int` → `Long` |
| `Float` | `Double` → `Float` |
| `Double` | `Int`, `Long`, `Float` → `Double` |
| `BigDecimal` | `Double`, `Long`, `Int`, `String` |
| `BigInt` | `Long`, `Int`, `String` |
| `List[A]` | `Seq` → `List` (each element decoded via `RawDecoder[A]`) |
| `Vector[A]` | Same as `List[A]` then converted |
| `Set[A]` | Same as `List[A]` then converted |
| `Map[String, V]` | `Map[?, ?]` with `String` keys |
| `Option[A]` | `null`/`None` → `None`; any other value decoded as `Some(a)` |
| `Either[A, B]` | `{"left": a}` or `{"right": b}` |

For custom types, provide a `given RawDecoder[MyType]` instance or use `@decodable` / `@extract`.

---
