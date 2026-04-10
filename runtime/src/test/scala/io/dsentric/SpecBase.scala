package io.dsentric

import org.scalatest.{Assertion, EitherValues, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.Assertions.succeed

/** Local test base for runtime module (not shared with core test classpath). */
abstract class SpecBase
    extends AnyFunSuite
    with Matchers
    with EitherValues
    with OptionValues:

  type Spec[-R, +E] = Unit

  def suite(name: String)(body: => Unit): Unit = body

  def assertTrue(conditions: Boolean*): Assertion =
    { conditions.foreach(_ shouldBe true); succeed }

  extension [A, B](e: Either[A, B])
    def flip: Either[B, A] = e match
      case Left(a)  => Right(a)
      case Right(b) => Left(b)
