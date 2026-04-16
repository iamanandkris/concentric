package io.concentric.internal

import scala.jdk.CollectionConverters.*

/**
 * Runtime helper invoked by macro-generated code to instantiate
 * [[io.concentric.annotations.FieldValidator]] classes named in
 * `@validateWith` annotations.
 *
 * Called once per field, at contract-derivation time
 * (i.e. when `given myContract = Contract.derived[MyClass]` is evaluated).
 */
private[concentric] object ValidatorHelper:

  /**
   * Instantiates the validator class identified by its fully-qualified Scala
   * name (which equals the Java binary name for top-level classes) and wraps
   * it as an `Any => List[String]` function.
   */
  def make(className: String): Any => List[String] =
    val inst =
      Class
        .forName(className)
        .getDeclaredConstructor()
        .newInstance()
        .asInstanceOf[io.concentric.annotations.FieldValidator[Any]]
    (v: Any) => inst.validate(v).asScala.toList

  /**
   * Instantiates the contract-level validator class identified by its
   * fully-qualified name and wraps it as a `T => List[String]` function.
   *
   * The class must implement [[io.concentric.ContractValidator]][T] and have
   * a public no-argument constructor.
   */
  def makeContractValidator[T](className: String): T => List[String] =
    val inst =
      Class
        .forName(className)
        .getDeclaredConstructor()
        .newInstance()
        .asInstanceOf[io.concentric.ContractValidator[T]]
    (t: T) => inst.validate(t)
