package io.concentric

import io.concentric.NonEmptyList


/**
 * Concrete [[Contract]][T] implementation.
 *
 * Instances are created exclusively by the [[internal.ContractMacro]]; users
 * should never construct this class directly.
 *
 * @param fieldMetas    List of [[FieldMeta]] — one per wire-level field.
 *                      For `@include` fields this list is *expanded*: all
 *                      inner type fields appear here individually, flattened.
 * @param isOpen        Whether unknown fields are allowed.
 * @param constructFn   Macro-generated lambda: validated fields map → T.
 *                      Called only after all violations have been collected;
 *                      callers guarantee that every required field is present
 *                      and type-correct before invoking it.
 * @param toRawFn       Macro-generated lambda: T → RawObject.  Handles
 *                      `@include` field flattening automatically.
 */
final class ContractImpl[T](
  val fieldMetas:          List[FieldMeta],
  val isOpen:              Boolean,
  val constructFn:         Map[String, Any] => T,
  val toRawFn:             T => RawObject,
  val contractValidators:  List[T => List[String]] = Nil
) extends Contract[T]:

  // Index for O(1) lookup
  private val metaByName: Map[String, FieldMeta] = fieldMetas.map(m => m.name -> m).toMap

  // ── validate ───────────────────────────────────────────────────────────────

  def validate(raw: RawObject): Either[ContractViolations, T] =
    runValidation(raw)

  // ── validatePatch ──────────────────────────────────────────────────────────

  def validatePatch(currentRaw: RawObject, patch: RawObject): Either[ContractViolations, T] =
    runPatchValidation(currentRaw, patch)

  // ── validatePartial ────────────────────────────────────────────────────────

  def validatePartial(raw: RawObject): Either[ContractViolations, Draft[T]] =
    runPartialValidation(raw)

  // ── collectViolations ──────────────────────────────────────────────────────

  def collectViolations(raw: RawObject): List[Violation] =
    runValidation(raw) match
      case Left(cv) => cv.violations.toList
      case Right(_) => Nil

  // ── sanitize ───────────────────────────────────────────────────────────────

  def sanitize(raw: RawObject): RawObject =
    raw
      // Drop internal fields
      .filterNot { case (k, _) => metaByName.get(k).exists(_.isInternal) }
      // Replace masked field values, or recursively sanitize nested contracts
      .map { case (k, v) =>
        metaByName.get(k).flatMap(_.masked) match
          case Some(mask) => k -> mask
          case None       =>
            // Apply nested sanitizer when present (e.g. List[Address] field)
            val transformed = metaByName.get(k).flatMap(_.nestedSanitize).fold(v)(fn => fn(v))
            k -> transformed
      }

  // ── toRaw ──────────────────────────────────────────────────────────────────

  def toRaw(t: T): RawObject = toRawFn(t)

  // ── jsonSchema ─────────────────────────────────────────────────────────────

  def jsonSchema: Map[String, Any] =
    val properties: Map[String, Any] =
      fieldMetas.map(meta => meta.name -> buildFieldSchema(meta)).toMap

    val requiredFields: List[String] =
      fieldMetas.filter(_.isRequired).map(_.name)

    val schema = collection.mutable.Map[String, Any](
      "$schema"    -> "http://json-schema.org/draft-07/schema#",
      "type"       -> "object",
      "properties" -> properties
    )
    if requiredFields.nonEmpty          then schema("required")             = requiredFields
    if !isOpen                          then schema("additionalProperties") = false

    schema.toMap

  private def buildFieldSchema(meta: FieldMeta): Map[String, Any] =
    // ── Type-level schema ──────────────────────────────────────────────────
    val typeSchema: Map[String, Any] = meta.schemaType match
      case "array" =>
        val itemSchema: Map[String, Any] = meta.schemaFn match
          case Some(fn) => fn() - "$schema"
          case None     =>
            if meta.arrayItemType != "any" then Map("type" -> meta.arrayItemType)
            else Map.empty[String, Any]
        if itemSchema.nonEmpty then Map[String, Any]("type" -> "array", "items" -> itemSchema)
        else Map[String, Any]("type" -> "array")
      case "object" =>
        meta.schemaFn match
          case Some(fn) => fn() - "$schema"
          case None     => Map[String, Any]("type" -> "object")
      case "any"    => Map.empty[String, Any]
      case t        => Map[String, Any]("type" -> t)

    // ── Constraint annotations ─────────────────────────────────────────────
    val constraintBuf = collection.mutable.Map[String, Any]()
    meta.minLength.foreach(v => constraintBuf("minLength") = v)
    meta.maxLength.foreach(v => constraintBuf("maxLength") = v)
    meta.min.foreach      (v => constraintBuf("minimum")   = v)
    meta.max.foreach      (v => constraintBuf("maximum")   = v)
    // @pattern and @extract both map to JSON Schema "pattern"
    meta.pattern.foreach        (v => constraintBuf("pattern") = v)
    meta.extractPattern.foreach (v => constraintBuf("pattern") = v)
    if meta.isEmail   then constraintBuf("format") = "email"
    if meta.isUrl     then constraintBuf("format") = "uri"
    if meta.isUuid    then constraintBuf("format") = "uuid"
    if meta.isFuture  then constraintBuf("x-future") = true
    if meta.isPast    then constraintBuf("x-past")   = true
    if meta.isPositive then constraintBuf("exclusiveMinimum") = 0
    meta.multipleOf.foreach(v => constraintBuf("multipleOf") = v)
    if meta.isNonEmpty then
      meta.schemaType match
        case "array" => constraintBuf("minItems") = 1
        case _       =>
          // Only add minLength:1 if minLength is not already set to >= 1
          if meta.minLength.forall(_ < 1) then constraintBuf("minLength") = 1

    // ── Extension annotations ──────────────────────────────────────────────
    val extBuf = collection.mutable.Map[String, Any]()
    if meta.isImmutable then extBuf("x-immutable") = true
    if meta.isInternal  then extBuf("x-internal")  = true
    if meta.isReserved  then extBuf("x-reserved")  = true
    meta.masked.foreach(v => extBuf("x-masked") = v)

    typeSchema ++ constraintBuf ++ extBuf

  // ── extraFields ────────────────────────────────────────────────────────────

  def extraFields(raw: RawObject): RawObject =
    raw.filterNot { case (k, _) => metaByName.contains(k) }

  // ── private helpers ────────────────────────────────────────────────────────

  private def runValidation(raw: RawObject): Either[ContractViolations, T] =
    val violations     = collection.mutable.ArrayBuffer.empty[Violation]
    val validatedFields = collection.mutable.HashMap.empty[String, Any]

    // 1. Check for unknown fields on closed contracts
    if !isOpen then
      raw.keys.foreach { key =>
        if !metaByName.contains(key) then
          violations += Violation(
            FieldPath(key), ViolationCode.UnknownField,
            s"Unknown field '$key' (contract is closed)"
          )
      }

    // 2. Per-field validation
    fieldMetas.foreach { meta =>
      val path = FieldPath(meta.name)
      raw.get(meta.name) match

        // Field is absent
        case None =>
          if meta.isRequired then
            violations += Violation(path, ViolationCode.ExpectedMissing,
              s"'${meta.name}' is required")
          // Optional / has-default: constructFn will supply None / default

        // Field is present
        case Some(rawValue) =>
          if meta.isReserved then
            violations += Violation(path, ViolationCode.ReservedField,
              s"'${meta.name}' is reserved and cannot be set")
          else
            meta.decoder(rawValue) match
              case None =>
                val typeName = if rawValue == null then "null" else rawValue.getClass.getSimpleName
                violations += Violation(path,
                  ViolationCode.TypeMismatch("declared type", typeName),
                  s"'${meta.name}' has an incorrect type")
              case Some(decoded) =>
                // Constraint annotations on the decoded value
                val constraintViolations = meta.validateConstraints(decoded, path)
                // Nested contract violations (e.g. invalid items inside a List[Address])
                val nestedViolations = meta.nestedCollect.toList.flatMap(_.apply(rawValue, path))
                val allViolations = constraintViolations ++ nestedViolations
                if allViolations.isEmpty then
                  validatedFields(meta.name) = decoded
                else
                  violations ++= allViolations
    }

    if violations.nonEmpty then
      Left(ContractViolations(NonEmptyList.fromIterableUnsafe(violations)))
    else
      val t = constructFn(validatedFields.toMap)
      // Run cross-field contract validators only after all field checks pass.
      val contractViolations: List[Violation] = contractValidators.flatMap { validator =>
        validator(t).map { msg =>
          Violation(
            FieldPath(Nil),
            ViolationCode.ConstraintFailed("validateContract"),
            msg
          )
        }
      }
      if contractViolations.nonEmpty then
        Left(ContractViolations(NonEmptyList.fromIterableUnsafe(contractViolations)))
      else
        Right(t)

  /**
   * Validates a partial update (patch) against the current stored state.
   *
   * Each field is treated according to its origin:
   *
   *  - **Patch fields** (present in `patch`) receive full validation: type
   *    decoding, all constraint annotations, [[io.concentric.annotations.immutable]],
   *    and [[io.concentric.annotations.reserved]] guards.
   *
   *  - **Current fields** (present in `currentRaw` but absent from `patch`) are
   *    decoded and trusted without re-checking constraints.  The patch is only
   *    responsible for the fields it explicitly touches; re-validating stored
   *    fields would break patches whenever stored data predates a tightened
   *    constraint, and would also block legitimate patches on records that
   *    happen to contain `@reserved` fields written by the system.
   *
   * Unknown-field checks apply only to `patch`; `currentRaw` is system-trusted.
   *
   * For nested object fields in the patch, the patch's nested map is deep-merged
   * with the corresponding `currentRaw` nested map so that the nested contract
   * validator sees the complete merged state, not just the partial patch.
   */
  private def runPatchValidation(currentRaw: RawObject, patch: RawObject): Either[ContractViolations, T] =
    val violations      = collection.mutable.ArrayBuffer.empty[Violation]
    val validatedFields = collection.mutable.HashMap.empty[String, Any]

    // 1. Unknown fields — only the patch is checked; currentRaw is system-trusted
    if !isOpen then
      patch.keys.foreach { key =>
        if !metaByName.contains(key) then
          violations += Violation(
            FieldPath(key), ViolationCode.UnknownField,
            s"Unknown field '$key'"
          )
      }

    // 2. Per-field: patch fields are validated fully; current fields are decoded and trusted
    fieldMetas.foreach { meta =>
      val path = FieldPath(meta.name)

      patch.get(meta.name) match

        // ── Field IS in the patch — full validation applies ─────────────────
        case Some(rawPatchValue) =>
          if meta.isImmutable then
            violations += Violation(path, ViolationCode.ImmutableField,
              s"'${meta.name}' is immutable and cannot be changed")
          else if meta.isReserved then
            violations += Violation(path, ViolationCode.ReservedField,
              s"'${meta.name}' is reserved and cannot be set")
          else
            // For nested objects: deep-merge the patch value onto the currentRaw
            // value so that the nested contract validator sees the full merged state.
            val effectiveValue = (currentRaw.get(meta.name), rawPatchValue) match
              case (Some(currNested: Map[?, ?]), patchNested: Map[?, ?]) =>
                RawObjectOps.deltaTraverseConcat(
                  currNested.asInstanceOf[RawObject],
                  patchNested.asInstanceOf[RawObject]
                )
              case _ => rawPatchValue

            meta.decoder(effectiveValue) match
              case None =>
                val typeName = if rawPatchValue == null then "null" else rawPatchValue.getClass.getSimpleName
                violations += Violation(path,
                  ViolationCode.TypeMismatch("declared type", typeName),
                  s"'${meta.name}' has an incorrect type")
              case Some(decoded) =>
                val constraintViolations = meta.validateConstraints(decoded, path)
                val nestedViolations     =
                  meta.nestedPatchCollect
                    .map(_.apply(currentRaw.getOrElse(meta.name, null), rawPatchValue, path))
                    .orElse(meta.nestedCollect.map(_.apply(effectiveValue, path)))
                    .toList
                    .flatten
                val allViolations        = constraintViolations ++ nestedViolations
                if allViolations.isEmpty then validatedFields(meta.name) = decoded
                else violations ++= allViolations

        // ── Field NOT in the patch — use currentRaw, skip re-validation ─────
        case None =>
          currentRaw.get(meta.name) match
            case Some(rawCurrentValue) =>
              meta.decoder(rawCurrentValue) match
                case Some(decoded) =>
                  validatedFields(meta.name) = decoded
                case None =>
                  // A value in currentRaw cannot be decoded — surface it so
                  // schema drift or data corruption does not silently corrupt T.
                  val typeName = if rawCurrentValue == null then "null" else rawCurrentValue.getClass.getSimpleName
                  violations += Violation(path,
                    ViolationCode.TypeMismatch("declared type", typeName),
                    s"'${meta.name}' in current record has an unexpected type")
            case None =>
              if meta.isRequired then
                violations += Violation(path, ViolationCode.ExpectedMissing,
                  s"'${meta.name}' is required")
    }

    if violations.nonEmpty then
      Left(ContractViolations(NonEmptyList.fromIterableUnsafe(violations)))
    else
      val t = constructFn(validatedFields.toMap)
      // Cross-field validators run on the fully assembled (patch + current) result
      val contractViolations: List[Violation] = contractValidators.flatMap { validator =>
        validator(t).map { msg =>
          Violation(
            FieldPath(Nil),
            ViolationCode.ConstraintFailed("validateContract"),
            msg
          )
        }
      }
      if contractViolations.nonEmpty then
        Left(ContractViolations(NonEmptyList.fromIterableUnsafe(contractViolations)))
      else
        Right(t)

  private def runPartialValidation(raw: RawObject): Either[ContractViolations, Draft[T]] =
    val violations      = collection.mutable.ArrayBuffer.empty[Violation]
    val validatedFields = collection.mutable.HashMap.empty[String, Any]

    // Unknown-field check still applies — a partial object must still respect
    // the contract's schema; we just don't require all fields to be present.
    if !isOpen then
      raw.keys.foreach { key =>
        if !metaByName.contains(key) then
          violations += Violation(
            FieldPath(key), ViolationCode.UnknownField,
            s"Unknown field '$key' (contract is closed)"
          )
      }

    fieldMetas.foreach { meta =>
      val path = FieldPath(meta.name)
      raw.get(meta.name) match

        // Absent — skip silently (no ExpectedMissing in partial mode)
        case None => ()

        case Some(rawValue) =>
          if meta.isReserved then
            violations += Violation(path, ViolationCode.ReservedField,
              s"'${meta.name}' is reserved and cannot be set")
          else
            meta.decoder(rawValue) match
              case None =>
                violations += Violation(path,
                  ViolationCode.TypeMismatch("declared type", rawValue.getClass.getSimpleName),
                  s"'${meta.name}' has an incorrect type")
              case Some(decoded) =>
                val constraintViolations = meta.validateConstraints(decoded, path)
                val nestedViolations = meta.nestedCollect.toList.flatMap(_.apply(rawValue, path))
                val allViolations = constraintViolations ++ nestedViolations
                if allViolations.isEmpty then
                  validatedFields(meta.name) = decoded
                else
                  violations ++= allViolations
    }

    if violations.nonEmpty then
      Left(ContractViolations(NonEmptyList.fromIterableUnsafe(violations)))
    else
      Right(new Draft[T](validatedFields.toMap))
