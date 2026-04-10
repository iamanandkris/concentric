package io.dsentric

import scala.jdk.CollectionConverters.*

/**
 * Synchronous, annotation-driven contract for Java and Kotlin consumers.
 *
 * == How it works ==
 *
 * [[JvmContract]] combines two pieces:
 *
 *  1. [[JvmContractDeriver]] reads dsentric annotations from the Java/Kotlin
 *     class via runtime reflection and produces a [[List]][[[FieldMeta]]] —
 *     the same metadata type used by the Scala macro path.
 *
 *  2. Those `FieldMeta` objects are handed to [[ContractImpl]], the same
 *     validation engine used by `Contract.derived[T]`.  This means every
 *     constraint (`@nonEmpty`, `@min`, `@email`, etc.), every policy check
 *     (`@reserved`, `@immutable`, unknown-field rejection), and every patch
 *     semantic is identical to the Scala path.
 *
 * == Usage (Java record) ==
 *
 * {{{
 * import io.dsentric.annotations.*;
 *
 * @contract
 * public record User(
 *     @immutable Long id,
 *     @nonEmpty  String name,
 *     @min(0)    int age
 * ) {}
 *
 * // Instantiate once (thread-safe):
 * JvmContract<User> userContract = JvmContract.of(
 *     User.class,
 *     fields -> new User(
 *         (Long)   fields.get("id"),
 *         (String) fields.get("name"),
 *         ((Number) fields.get("age")).intValue()
 *     )
 * );
 *
 * // Validate:
 * var raw = Map.of("id", 1L, "name", "Alice", "age", 30);
 * ValidationResult<User> result = userContract.validate(raw);
 * if (result.isValid()) { User u = result.getValue().get(); }
 * }}}
 *
 * == Usage (Kotlin data class) ==
 *
 * {{{
 * @contract
 * data class User(
 *     @field:immutable val id: Long,   // @field: puts annotation on the JVM field
 *     @field:nonEmpty  val name: String,
 *     @field:min(0)    val age: Int = 0
 * )
 *
 * val userContract = JvmContract.of(User::class.java) { fields ->
 *     User(
 *         id   = fields["id"]   as Long,
 *         name = fields["name"] as String,
 *         age  = (fields["age"] as? Int) ?: 0
 *     )
 * }
 * }}}
 *
 * @param clazz       The Java/Kotlin class representing the contract type.
 * @param constructFn A function that receives a `Map<String, Object>` of
 *                    validated field values and produces a `T`.  The map
 *                    contains every required field (guaranteed to be present
 *                    and type-correct) and every optional [[java.util.Optional]]
 *                    field (either `Optional.of(v)` or `Optional.empty()`).
 * @param isOpen      When `true` unknown fields are silently accepted.
 *                    Defaults to `false` (closed — strict mode).
 */
class JvmContract[T](
  clazz:       Class[T],
  constructFn: java.util.function.Function[java.util.Map[String, AnyRef], T],
  isOpen:      Boolean = false
):

  val fieldMetas: List[FieldMeta] = JvmContractDeriver.derive(clazz)

  // ── Internal ContractImpl wiring ──────────────────────────────────────────

  /** Convert a validated Scala Map → Java Map and call the user's constructFn. */
  private val scalaConstructFn: Map[String, Any] => T = (fields: Map[String, Any]) =>
    val javaMap = new java.util.HashMap[String, AnyRef]()

    fields.foreach { (k, v) =>
      // For Optional fields, convert Scala Option back to java.util.Optional
      fieldMetas.find(_.name == k).fold {
        javaMap.put(k, v.asInstanceOf[AnyRef])
      } { meta =>
        if meta.isOptional then
          val opt: java.util.Optional[AnyRef] = v match
            case Some(inner) => java.util.Optional.of(inner.asInstanceOf[AnyRef])
            case None        => java.util.Optional.empty[AnyRef]()
            case jOpt: java.util.Optional[?] => jOpt.asInstanceOf[java.util.Optional[AnyRef]]
            case other       => java.util.Optional.of(other.asInstanceOf[AnyRef])
          javaMap.put(k, opt)
        else
          javaMap.put(k, v.asInstanceOf[AnyRef])
      }
    }
    // Ensure every Optional field that was absent from raw arrives as Optional.empty()
    fieldMetas.foreach { meta =>
      if meta.isOptional && !javaMap.containsKey(meta.name) then
        javaMap.put(meta.name, java.util.Optional.empty[AnyRef]())
    }
    constructFn.apply(javaMap)

  /** Reflective toRaw: T → RawObject. */
  private val toRawFn: T => RawObject = (t: T) =>
    def productToMap(v: Any): Map[String, Any] = v match
      case m: java.util.Map[?, ?] =>
        m.asInstanceOf[java.util.Map[String, Any]].asScala.toMap
      case m: Map[?, ?] =>
        m.asInstanceOf[Map[String, Any]]
      case p: Product =>
        p.productElementNames.zip(p.productIterator).map { case (n, vv) => n -> vv }.toMap
      case other =>
        Map("value" -> other)

    fieldMetas.flatMap { meta =>
      val f = clazz.getDeclaredField(meta.name)
      f.setAccessible(true)
      val v = f.get(t)
      // Convert java.util.Optional back to Scala Option for the raw map
      val scalaV: Any = v match
        case opt: java.util.Optional[?] => if opt.isEmpty then None else Some(opt.get)
        case other                       => other

      if scalaV == null then None
      else
        // Handle @discriminator on Either fields: emit a tagged map inside the field
        val discrOpt = Option(f.getAnnotation(classOf[io.dsentric.annotations.discriminator]))
        val encoded: Any = (discrOpt, scalaV) match
          case (Some(discr), e: scala.util.Either[?, ?]) =>
            val base: Map[String, Any] = e match
              case scala.util.Left(l)  => productToMap(l)
              case scala.util.Right(r) => productToMap(r)
            val tag: String = e match
              case scala.util.Left(_)  => discr.left()
              case scala.util.Right(_) => discr.right()
            base + (discr.value() -> tag)
          case (None, e: scala.util.Either[?, ?]) =>
            e match
              case scala.util.Left(l)  => Map("left"  -> productToMap(l))
              case scala.util.Right(r) => Map("right" -> productToMap(r))
          case _ =>
            scalaV

        Some(meta.name -> encoded)
    }.toMap

  private val impl: Contract[T] = new ContractImpl[T](
    fieldMetas         = fieldMetas,
    isOpen             = isOpen,
    constructFn        = scalaConstructFn,
    toRawFn            = toRawFn,
    contractValidators = Nil
  )

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Validate a raw map and return a synchronous [[ValidationResult]].
   *
   * The input map should contain string keys and raw JVM values
   * (`String`, `Integer`, `Long`, `Double`, `Boolean`, `Map`, `List`, …).
   */
  def validate(raw: java.util.Map[String, AnyRef]): ValidationResult[T] =
    toResult(impl.validate(toScalaRaw(raw)))

  /**
   * Validate a partial update (patch) against the current stored state.
   *
   * Fields present in `patch` are fully validated (type-check + constraints +
   * `@immutable` / `@reserved` guards).  Fields absent from `patch` are taken
   * from `current` and trusted without re-validation — the same semantics as
   * the Scala [[Contract.validatePatch]] method.
   */
  def validatePatch(
    current: java.util.Map[String, AnyRef],
    patch:   java.util.Map[String, AnyRef]
  ): ValidationResult[T] =
    toResult(impl.validatePatch(toScalaRaw(current), toScalaRaw(patch)))

  /**
   * Validate only the fields that are present in a partial raw map.
   *
   * Unlike [[validate]], absent required fields do **not** produce
   * `MISSING` violations.  All other checks (type decoding, constraint
   * annotations, unknown-field rejection) still apply to the fields that
   * ARE present.  This is the right tool for step-by-step / multi-page
   * form validation where you want per-field feedback before the user
   * has filled in everything.
   *
   * Returns an empty list when every supplied field is valid.
   * Returns a non-empty list of [[JvmViolation]]s otherwise.
   *
   * Note: because only a subset of fields may be present, a fully
   * constructed `T` is not available from this call.  Use [[validate]]
   * once the complete raw object is ready.
   */
  def validatePartial(raw: java.util.Map[String, AnyRef]): java.util.List[JvmViolation] =
    impl.validatePartial(toScalaRaw(raw)) match
      case Right(_)  => java.util.Collections.emptyList[JvmViolation]()
      case Left(cvs) => cvs.violations.map(JvmViolation.fromViolation).toList.asJava

  /**
   * Sanitize a raw map.
   *
   * Strips `@internal` fields and replaces `@masked` field values with the
   * mask string.  No validation is performed — the input is returned as-is
   * except for those two transformations.
   */
  def sanitize(raw: java.util.Map[String, AnyRef]): java.util.Map[String, AnyRef] =
    impl.sanitize(toScalaRaw(raw))
      .map { (k, v) => k -> v.asInstanceOf[AnyRef] }
      .asJava

  /**
   * Collect all validation violations without constructing `T`.
   *
   * Useful for building custom error-reporting pipelines.
   * Returns an empty list when the raw object is fully valid.
   */
  def collectViolations(raw: java.util.Map[String, AnyRef]): java.util.List[JvmViolation] =
    impl.collectViolations(toScalaRaw(raw))
      .map(JvmViolation.fromViolation)
      .asJava

  /**
   * Serialize an already-constructed `T` back to a raw map.
   *
   * The returned map contains only the non-null fields; `Optional.empty()`
   * fields are omitted.
   */
  def toRaw(t: T): java.util.Map[String, AnyRef] =
    toRawFn(t).map { (k, v) => k -> v.asInstanceOf[AnyRef] }.asJava

  /**
   * Serialize `t` to a compact JSON string.
   *
   * Equivalent to `RawJson.stringify(toRaw(t))`.
   *
   * {{{
   * String json = userContract.toJson(user);
   * // → {"age":30,"email":"alice@example.com","id":1,"name":"Alice"}
   * }}}
   */
  def toJson(t: T): String = RawJson.stringify(toRawFn(t))

  /**
   * Serialize `t` to an indented JSON string.
   *
   * @param indent  Number of spaces per indentation level (e.g. `2`).
   */
  def toJson(t: T, indent: Int): String = RawJson.stringify(toRawFn(t), indent)

  /**
   * Sanitize `raw` and return the result as a compact JSON string.
   *
   * `@internal` fields are stripped and `@masked` fields are replaced before
   * serialisation, identical to `RawJson.stringify(sanitize(raw))`.
   *
   * {{{
   * String json = userContract.sanitizeJson(storedRaw);
   * // id is gone (@internal), password is "***" (@masked)
   * }}}
   */
  def sanitizeJson(raw: java.util.Map[String, AnyRef]): String =
    RawJson.stringify(impl.sanitize(toScalaRaw(raw)))

  /**
   * Sanitize `raw` and return the result as an indented JSON string.
   *
   * @param indent  Number of spaces per indentation level (e.g. `2`).
   */
  def sanitizeJson(raw: java.util.Map[String, AnyRef], indent: Int): String =
    RawJson.stringify(impl.sanitize(toScalaRaw(raw)), indent)

  /**
   * Derive a JSON Schema (draft-07) document for this contract and return it
   * as a compact JSON string.
   *
   * This is the most convenient form for Java/Kotlin callers — the string can
   * be returned directly from a `/schema` endpoint, passed to Jackson/Gson for
   * further processing, or logged for debugging.
   *
   * {{{
   * // Java
   * String schema = userContract.jsonSchemaJson();
   * // → {"$schema":"http://json-schema.org/draft-07/schema#","additionalProperties":false,...}
   *
   * // Kotlin
   * val schema: String = userContract.jsonSchemaJson()
   * }}}
   */
  def jsonSchemaJson(): String = RawJson.stringify(impl.jsonSchema)

  /**
   * Derive a JSON Schema (draft-07) document for this contract and return it
   * as an indented JSON string.
   *
   * @param indent  Number of spaces per indentation level (e.g. `2`).
   */
  def jsonSchemaJson(indent: Int): String = RawJson.stringify(impl.jsonSchema, indent)

  /**
   * Derive a JSON Schema (draft-07) document for this contract as a
   * deeply-converted `java.util.Map`.
   *
   * Useful when you need to programmatically inspect or mutate the schema
   * structure (e.g. to merge it with an existing OpenAPI document).  Nested
   * maps and arrays are fully converted to Java types:
   *
   *  - `Map[String, Any]`  →  `java.util.LinkedHashMap<String, Object>`
   *  - `List[_]`           →  `java.util.ArrayList<Object>`
   *  - Scalars             →  boxed as-is (`String`, `Integer`, `Boolean`, …)
   *
   * {{{
   * // Java
   * Map<String, Object> schema = userContract.jsonSchema();
   * Map<?, ?> props = (Map<?, ?>) schema.get("properties");
   * }}}
   */
  def jsonSchema(): java.util.Map[String, AnyRef] =
    deepToJava(impl.jsonSchema).asInstanceOf[java.util.Map[String, AnyRef]]

  /**
   * Return the extra (unknown) fields present in a raw object that are not
   * declared by this contract.
   *
   * This is primarily useful for open contracts. Nested maps and arrays are
   * converted to Java collection types in the same way as [[jsonSchema()]].
   */
  def extraFields(raw: java.util.Map[String, AnyRef]): java.util.Map[String, AnyRef] =
    deepToJava(impl.extraFields(toScalaRaw(raw))).asInstanceOf[java.util.Map[String, AnyRef]]

  // ── Helpers ───────────────────────────────────────────────────────────────

  /**
   * Convert a Java raw map to the Scala [[RawObject]] expected by [[ContractImpl]].
   *
   * For `Optional<T>` fields (identified by `isOptional` on [[FieldMeta]]) we
   * pre-process the value so that [[ContractImpl]] sees only the unwrapped inner
   * value — or nothing at all when the Optional is empty:
   *
   *  - `Optional.of(v)` → inner value `v` is placed in the Scala map.
   *    [[ContractImpl]] decodes `v` normally and constraint annotations (e.g.
   *    `@min`) are applied to `v`, not to the `Optional` wrapper.
   *
   *  - `Optional.empty()` or `null` → key is **omitted** from the Scala map.
   *    [[ContractImpl]] sees the field as absent; since `isOptional = true` no
   *    `ExpectedMissing` violation is raised and no constraints are checked.
   *
   * Non-optional fields are passed through as-is (null values are kept so that
   * [[ContractImpl]] can detect null-for-required-field as `TypeMismatch`).
   */
  /** Recursively convert a Scala Map/List structure to Java Map/List. */
  private def deepToJava(value: Any): AnyRef = value match
    case m: Map[?, ?] =>
      val jm = new java.util.LinkedHashMap[String, AnyRef]()
      m.asInstanceOf[Map[String, Any]].foreach { (k, v) => jm.put(k, deepToJava(v)) }
      jm
    case l: List[?] =>
      val jl = new java.util.ArrayList[AnyRef]()
      l.foreach { v => jl.add(deepToJava(v)) }
      jl
    case b: Boolean  => java.lang.Boolean.valueOf(b)
    case i: Int      => java.lang.Integer.valueOf(i)
    case l: Long     => java.lang.Long.valueOf(l)
    case d: Double   => java.lang.Double.valueOf(d)
    case other       => other.asInstanceOf[AnyRef]

  private def toScalaRaw(javaMap: java.util.Map[String, AnyRef]): RawObject =
    val optionalNames: Set[String] = fieldMetas.filter(_.isOptional).map(_.name).toSet
    val builder = Map.newBuilder[String, Any]
    javaMap.asScala.foreach { (k, v) =>
      if optionalNames.contains(k) then
        v match
          case opt: java.util.Optional[?] if opt.isEmpty => () // absent — omit key
          case opt: java.util.Optional[?]                => builder += k -> opt.get()
          case null                                      => () // null — treat as absent
          case other                                     => builder += k -> other
      else
        builder += k -> v
    }
    builder.result()

  private def toResult(either: Either[ContractViolations, T]): ValidationResult[T] =
    either match
      case Right(t)  => ValidationResult.success(t)
      case Left(cvs) =>
        ValidationResult.failure(cvs.violations.map(JvmViolation.fromViolation).toList)


object JvmContract:

  private def derivedOpen[T](clazz: Class[T]): Boolean =
    Option(clazz.getAnnotation(classOf[io.dsentric.annotations.contract]))
      .exists(_.open())

  /**
   * Create a [[JvmContract]] for a Java/Kotlin class.
   *
   * @param clazz       The class to derive the contract from (must be annotated
   *                    with `@contract` and have field-level or parameter-level
   *                    dsentric annotations). The contract's openness is read
   *                    from `@contract(open = ...)`.
   * @param constructFn A function that maps validated field values to `T`.
   */
  def of[T](
    clazz: Class[T],
    constructFn: java.util.function.Function[java.util.Map[String, AnyRef], T]
  ): JvmContract[T] = new JvmContract(clazz, constructFn, derivedOpen(clazz))

  /**
   * Overload that explicitly overrides the openness declared on `@contract`.
   */
  def of[T](
    clazz: Class[T],
    constructFn: java.util.function.Function[java.util.Map[String, AnyRef], T],
    open: Boolean
  ): JvmContract[T] = new JvmContract(clazz, constructFn, open)

  /**
   * Create a [[JvmContract]] for a **Java record** without writing a constructor
   * function.
   *
   * The library automatically discovers the canonical constructor via
   * `getRecordComponents()` (Java 16+) and builds the reflective invocation for
   * you.  Type coercions are handled by the validation engine before the
   * constructor is called, so every field value is already the right boxed type.
   *
   * {{{
   * // Before — verbose, error-prone:
   * JvmContract<User> c = JvmContract.of(
   *     User.class,
   *     fields -> new User(
   *         (Long)   fields.get("id"),
   *         (String) fields.get("name"),
   *         (String) fields.get("email"),
   *         ((Number) fields.get("age")).intValue(),
   *         (String) fields.get("password")
   *     )
   * );
   *
   * // After — zero boilerplate:
   * JvmContract<User> c = JvmContract.ofRecord(User.class);
   * }}}
   *
   * The contract's openness is read from `@contract(open = ...)`.
   *
   * @throws IllegalArgumentException if the class is not a record or runs on JVM < 16.
   */
  def ofRecord[T](clazz: Class[T]): JvmContract[T] =
    new JvmContract(clazz, JvmContractDeriver.buildRecordConstructFn(clazz), derivedOpen(clazz))

  /**
   * Overload that explicitly overrides the openness declared on `@contract`.
   */
  def ofRecord[T](clazz: Class[T], open: Boolean): JvmContract[T] =
    new JvmContract(clazz, JvmContractDeriver.buildRecordConstructFn(clazz), open)

  /**
   * Create a [[JvmContract]] for a **Kotlin data class** (or Java POJO) without
   * writing a constructor function.
   *
   * Uses the primary non-synthetic constructor — the one with the most parameters
   * that is not a Kotlin default-argument synthetic constructor.  Field values are
   * passed in the same order as `JvmContractDeriver.derive` returns them (which
   * matches the primary constructor's declaration order).
   *
   * {{{
   * // Kotlin — before:
   * val userContract = JvmContract.of(User::class.java) { fields ->
   *     User(
   *         id       = fields["id"]   as Long,
   *         name     = fields["name"] as String,
   *         email    = fields["email"] as String,
   *         age      = (fields["age"]  as? Int) ?: 0,
   *         password = fields["password"] as? String
   *     )
   * }
   *
   * // Kotlin — after:
   * val userContract = JvmContract.ofPrimary(User::class.java)
   * }}}
   *
   * The contract's openness is read from `@contract(open = ...)`.
   *
   * @throws IllegalArgumentException if no non-synthetic constructor is found.
   */
  def ofPrimary[T](clazz: Class[T]): JvmContract[T] =
    // derive once here to get field order; JvmContract ctor will derive again.
    // Both calls are at startup (not per-request) so the cost is negligible.
    val fieldOrder = JvmContractDeriver.derive(clazz).map(_.name)
    new JvmContract(
      clazz,
      JvmContractDeriver.buildPrimaryConstructFn(clazz, fieldOrder),
      derivedOpen(clazz)
    )

  /**
   * Overload that explicitly overrides the openness declared on `@contract`.
   */
  def ofPrimary[T](clazz: Class[T], open: Boolean): JvmContract[T] =
    val fieldOrder = JvmContractDeriver.derive(clazz).map(_.name)
    new JvmContract(clazz, JvmContractDeriver.buildPrimaryConstructFn(clazz, fieldOrder), open)
