package io.concentric

import scala.jdk.CollectionConverters.*

/**
 * JVM wrapper around [[Draft]][T] for use in multi-step form validation
 * workflows.
 *
 * Instances are created via [[JvmContract.validatePartialAsDraft]] or
 * [[JvmDraft.empty]].  Two drafts can be merged and then finalised:
 *
 * {{{
 * // Kotlin
 * val r1 = userContract.validatePartialAsDraft(step1Raw)
 * val r2 = userContract.validatePartialAsDraft(step2Raw)
 * if (r1.isValid && r2.isValid) {
 *     val result = r1.draft.get().merge(r2.draft.get()).finalize(userContract)
 * }
 * }}}
 */
final class JvmDraft[T] private[concentric] (private[concentric] val draft: Draft[T]):

  /** Merge another [[JvmDraft]][T] into this one (right-biased). */
  def merge(other: JvmDraft[T]): JvmDraft[T] =
    new JvmDraft[T](draft.merge(other.draft))

  /**
   * Attempt to finalise this draft into a fully-constructed `T`.
   *
   * Delegates to [[JvmContract.finalizeDraft]] which runs [[Contract.validate]]
   * on the accumulated fields.
   */
  def finalize(contract: JvmContract[T]): ValidationResult[T] =
    contract.finalizeDraft(draft)

  /** True when no fields have been validated yet. */
  def isEmpty: Boolean = draft.isEmpty

  /** Number of validated fields in this draft. */
  def fieldCount: Int = draft.size

  override def toString: String = s"JvmDraft(${draft})"

object JvmDraft:
  /** An empty draft with no validated fields. */
  def empty[T]: JvmDraft[T] = new JvmDraft[T](Draft.empty[T])


/**
 * Result of [[JvmContract.validatePartialAsDraft]].
 *
 * When all supplied fields are valid, [[isValid]] is `true` and [[getDraft]]
 * holds a [[JvmDraft]] ready to be merged and finalised.
 *
 * When any field is invalid [[getErrors]] contains the violations and
 * [[getDraft]] is empty.
 */
final class JvmDraftResult[T](
  private val _errors: java.util.List[JvmViolation],
  private val _draft:  java.util.Optional[JvmDraft[T]]
):
  /** `true` when all supplied fields were valid. */
  def isValid: Boolean = _errors.isEmpty

  /** The list of validation violations; empty when [[isValid]] is `true`. */
  def getErrors: java.util.List[JvmViolation] = _errors

  /** Kotlin-style alias for [[getErrors]]. */
  def errors: java.util.List[JvmViolation] = getErrors

  /**
   * The validated draft; present only when [[isValid]] is `true`.
   * Returns [[java.util.Optional.empty]] on failure.
   */
  def getDraft: java.util.Optional[JvmDraft[T]] = _draft

  /** Kotlin-style alias for [[getDraft]]. */
  def draft: java.util.Optional[JvmDraft[T]] = getDraft

  override def toString: String =
    if isValid then s"JvmDraftResult.valid(${_draft.orElse(null)})"
    else            s"JvmDraftResult.invalid(${_errors.size} violations)"
