package io.dsentric

/**
 * Adapter over [[ContractImpl]] that exposes open-contract validation results.
 */
final class OpenContractImpl[T](impl: ContractImpl[T]) extends OpenContract[T]:

  def fieldMetas: List[FieldMeta] = impl.fieldMetas

  def validate(raw: RawObject): Either[ContractViolations, (T, RawObject)] =
    impl.validate(raw).map(value => (value, impl.extraFields(raw)))

  def collectViolations(raw: RawObject): List[Violation] =
    impl.collectViolations(raw)

  def extraFields(raw: RawObject): RawObject =
    impl.extraFields(raw)

  def toRaw(t: T): RawObject =
    impl.toRaw(t)

  def sanitize(raw: RawObject): RawObject =
    impl.sanitize(raw)

  def jsonSchema: Map[String, Any] =
    impl.jsonSchema
