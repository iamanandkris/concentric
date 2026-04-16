package io.concentric

/**
 * Type-class that decodes a raw JVM value (Any) into a typed value T.
 *
 * Instances are used by the macro-generated contract logic to convert the
 * untyped values coming from a RawObject (Map[String, Any]) into the precise
 * Scala types declared in the case class.
 *
 * New instances can be provided via given/implicit in the usual Scala 3 way.
 */
trait RawDecoder[T]:
  def decode(raw: Any): Option[T]

object RawDecoder:

  def apply[T](using d: RawDecoder[T]): RawDecoder[T] = d

  /**
   * Derive a [[RawDecoder]][T] for a single-field case class (value-class
   * wrapper, newtype, thin domain type, etc.).
   *
   * The generated decoder delegates to the inner field's decoder, so all
   * widening / narrowing coercions provided by that decoder are preserved:
   *
   * {{{
   *   @decodable
   *   case class Email(value: String)
   *   given RawDecoder[Email] = RawDecoder.derived[Email]
   *
   *   @decodable
   *   case class Age(value: Int)
   *   given RawDecoder[Age] = RawDecoder.derived[Age]
   *
   *   // Now Email / Age can be used as contract field types directly:
   *   @contract
   *   case class User(
   *     @email contactEmail: Email,
   *     name:  String,
   *     age:   Age
   *   )
   * }}}
   *
   * Only single-field case classes are supported.  Multi-field records should
   * use [[Contract.derived]] instead.
   */
  inline def derived[T <: Product]: RawDecoder[T] =
    ${ internal.DecodableMacro.derived[T] }

  // ── Primitives ────────────────────────────────────────────────────────────

  given RawDecoder[String] with
    def decode(raw: Any): Option[String] = raw match
      case s: String => Some(s)
      case _         => None

  given RawDecoder[Boolean] with
    def decode(raw: Any): Option[Boolean] = raw match
      case b: Boolean => Some(b)
      case _          => None

  given RawDecoder[Int] with
    def decode(raw: Any): Option[Int] = raw match
      case i: Int                    => Some(i)
      case l: Long if l.isValidInt   => Some(l.toInt)
      case s: String                 => s.toIntOption
      case _                         => None

  given RawDecoder[Long] with
    def decode(raw: Any): Option[Long] = raw match
      case l: Long   => Some(l)
      case i: Int    => Some(i.toLong)
      case s: String => s.toLongOption
      case _         => None

  given RawDecoder[Float] with
    def decode(raw: Any): Option[Float] = raw match
      case f: Float  => Some(f)
      case d: Double => Some(d.toFloat)
      case i: Int    => Some(i.toFloat)
      case l: Long   => Some(l.toFloat)
      case s: String => s.toFloatOption
      case _         => None

  given RawDecoder[Double] with
    def decode(raw: Any): Option[Double] = raw match
      case d: Double => Some(d)
      case f: Float  => Some(f.toDouble)
      case i: Int    => Some(i.toDouble)
      case l: Long   => Some(l.toDouble)
      case s: String => s.toDoubleOption
      case _         => None

  given RawDecoder[BigDecimal] with
    def decode(raw: Any): Option[BigDecimal] = raw match
      case bd: BigDecimal   => Some(bd)
      case d: Double        => Some(BigDecimal(d))
      case l: Long          => Some(BigDecimal(l))
      case i: Int           => Some(BigDecimal(i))
      case s: String        => scala.util.Try(BigDecimal(s)).toOption
      case _                => None

  given RawDecoder[BigInt] with
    def decode(raw: Any): Option[BigInt] = raw match
      case bi: BigInt  => Some(bi)
      case l: Long     => Some(BigInt(l))
      case i: Int      => Some(BigInt(i))
      case s: String   => scala.util.Try(BigInt(s)).toOption
      case _           => None

  // ── Collections ───────────────────────────────────────────────────────────

  given [A](using da: RawDecoder[A]): RawDecoder[List[A]] with
    def decode(raw: Any): Option[List[A]] = raw match
      case xs: Seq[?] => xs.toList.foldRight(Option(List.empty[A])) { (elem, acc) =>
        for
          rest    <- acc
          decoded <- da.decode(elem)
        yield decoded :: rest
      }
      case _ => None

  given [A](using da: RawDecoder[A]): RawDecoder[Vector[A]] with
    def decode(raw: Any): Option[Vector[A]] =
      summon[RawDecoder[List[A]]].decode(raw).map(_.toVector)

  given [A](using da: RawDecoder[A]): RawDecoder[Set[A]] with
    def decode(raw: Any): Option[Set[A]] =
      summon[RawDecoder[List[A]]].decode(raw).map(_.toSet)

  // ── Maps ──────────────────────────────────────────────────────────────────

  given [V](using dv: RawDecoder[V]): RawDecoder[Map[String, V]] with
    def decode(raw: Any): Option[Map[String, V]] = raw match
      case m: Map[?, ?] =>
        m.foldRight(Option(Map.empty[String, V])) { case ((k, v), acc) =>
          for
            rest    <- acc
            key     <- k match { case s: String => Some(s); case _ => None }
            decoded <- dv.decode(v)
          yield rest + (key -> decoded)
        }
      case _ => None

  // ── Option ────────────────────────────────────────────────────────────────

  /** Option[A]: None if the raw value is null / None, Some(a) if it decodes. */
  given [A](using da: RawDecoder[A]): RawDecoder[Option[A]] with
    def decode(raw: Any): Option[Option[A]] = raw match
      case null | None         => Some(None)
      case Some(inner)         => da.decode(inner).map(Some(_))
      case other               => da.decode(other).map(Some(_))

  // ── Either ────────────────────────────────────────────────────────────────

  /**
   * Either[A, B]: wire format is a single-key map {"left": value} or {"right": value}.
   *
   * Example round-trips:
   *   Left("err")   ↔  Map("left" -> "err")
   *   Right(42)     ↔  Map("right" -> 42)
   */
  given [A, B](using da: RawDecoder[A], db: RawDecoder[B]): RawDecoder[Either[A, B]] with
    def decode(raw: Any): Option[Either[A, B]] = raw match
      case m: Map[?, ?] =>
        val sm = m.asInstanceOf[Map[String, Any]]
        sm.get("left").flatMap(da.decode).map(Left(_))
          .orElse(sm.get("right").flatMap(db.decode).map(Right(_)))
      case _ => None
