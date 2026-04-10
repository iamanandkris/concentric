package io.dsentric

import io.dsentric.annotations.*

// ── Basic user models ─────────────────────────────────────────────────────────

/** A minimal contract used for basic required/optional/default tests. */
@contract
case class User(
  @immutable @internal          id:       Long,
  @nonEmpty  @maxLength(100)    name:     String,
                                email:    Option[String],
  @min(0)    @max(150)          age:      Int = 0,
  @masked                       password: Option[String]
)

/** A contract that allows additional properties. */
@contract(open = true)
case class OpenDoc(
  @nonEmpty title: String,
              body:  Option[String]
)

// ── Nested address / profile ──────────────────────────────────────────────────

@contract
case class Address(
  @nonEmpty street: String,
  @nonEmpty city:   String
)

given RawDecoder[Address] with
  def decode(raw: Any): Option[Address] = raw match
    case m: Map[?, ?] =>
      val sm = m.asInstanceOf[Map[String, Any]]
      for
        street <- sm.get("street").flatMap(RawDecoder[String].decode)
        city   <- sm.get("city").flatMap(RawDecoder[String].decode)
      yield Address(street, city)
    case _ => None

@contract
case class Profile(
  @immutable               userId:  Long,
  @nonEmpty @maxLength(50) handle:  String,
                           address: Option[Address]
)

// ── Nested collection ─────────────────────────────────────────────────────────

@contract
case class OrderItem(
  @nonEmpty         name: String,
  @min(1)           qty:  Int
)

given RawDecoder[OrderItem] with
  def decode(raw: Any): Option[OrderItem] = raw match
    case m: Map[?, ?] =>
      val sm = m.asInstanceOf[Map[String, Any]]
      for
        name <- sm.get("name").flatMap(RawDecoder[String].decode)
        qty  <- sm.get("qty").flatMap(RawDecoder[Int].decode)
      yield OrderItem(name, qty)
    case _ => None

@contract
case class Order(
  @nonEmpty                  id:    String,
  @nonEmpty                  items: List[OrderItem]
)

given RawDecoder[Order] with
  def decode(raw: Any): Option[Order] = raw match
    case m: Map[?, ?] =>
      val sm = m.asInstanceOf[Map[String, Any]]
      for
        id    <- sm.get("id").flatMap(RawDecoder[String].decode)
        items <- sm.get("items").flatMap(RawDecoder[List[OrderItem]].decode)
      yield Order(id, items)
    case _ => None

// ── Either model ──────────────────────────────────────────────────────────────

@contract
case class EitherHolder(
  @nonEmpty label: String,
             value: Either[String, Int]
)

// ── Constraint-heavy models ───────────────────────────────────────────────────

@contract
case class Ticket(
  @reserved                           trackingId: String = "PENDING",
  @nonEmpty                           title:      String,
                                      notes:      Option[String]
)

// Product is defined in ConstraintSpec.scala — it uses @validateWith which
// causes a Scala 3.4.2 compiler cycle when paired with a top-level
// given Contract[T] in a different initialization context.

@contract
case class ApiKey(
  @nonEmpty @minLength(8)      key:     String,
  @masked("REDACTED")          secret:  String,
                               label:   Option[String]
)

@contract
case class Prefs(
  language: Option[String],
  timezone: Option[String],
  theme:    Option[String]
)

// ── @discriminator models ─────────────────────────────────────────────────────

@contract
case class Cat(
  @nonEmpty name: String,
             indoor: Boolean = true
)

@contract
case class Dog(
  @nonEmpty name:  String,
  @min(1)   age:   Int
)

given RawDecoder[Cat] with
  def decode(raw: Any): Option[Cat] = raw match
    case m: Map[?, ?] =>
      val sm = m.asInstanceOf[Map[String, Any]]
      for name <- sm.get("name").flatMap(RawDecoder[String].decode)
      yield Cat(name, sm.get("indoor").flatMap(RawDecoder[Boolean].decode).getOrElse(true))
    case _ => None

given RawDecoder[Dog] with
  def decode(raw: Any): Option[Dog] = raw match
    case m: Map[?, ?] =>
      val sm = m.asInstanceOf[Map[String, Any]]
      for
        name <- sm.get("name").flatMap(RawDecoder[String].decode)
        age  <- sm.get("age").flatMap(RawDecoder[Int].decode)
      yield Dog(name, age)
    case _ => None

@contract
case class PetHolder(
  @nonEmpty                                                        ownerId: String,
  @discriminator("kind", left = "cat", right = "dog")
  pet:     Either[Cat, Dog]
)

// ── @include models ───────────────────────────────────────────────────────────

@contract
case class Timestamps(
  @immutable createdAt: Long,
             updatedAt: Long = 0L
)

@contract
case class Document(
  @nonEmpty             title:      String,
                        body:       Option[String],
  @include              timestamps: Timestamps
)

given RawDecoder[Timestamps] with
  def decode(raw: Any): Option[Timestamps] = raw match
    case m: Map[?, ?] =>
      val sm = m.asInstanceOf[Map[String, Any]]
      for ca <- sm.get("createdAt").flatMap(RawDecoder[Long].decode)
      yield Timestamps(ca, sm.get("updatedAt").flatMap(RawDecoder[Long].decode).getOrElse(0L))
    case _ => None

// ── @decodable wrapper types ──────────────────────────────────────────────────

@decodable
case class EmailAddr(value: String)
given RawDecoder[EmailAddr] = RawDecoder.derived[EmailAddr]

@decodable
case class Score(value: Int)
given RawDecoder[Score] = RawDecoder.derived[Score]

@decodable
case class UserId(value: Long)
given RawDecoder[UserId] = RawDecoder.derived[UserId]

@contract
case class Member(
  @immutable          id:      UserId,
  @nonEmpty           handle:  String,
  @email              contact: EmailAddr,
                      score:   Score = Score(0)
)

// ── @extract models ───────────────────────────────────────────────────────────

@decodable
@extract("(\\d{4})-(\\d{2})-(\\d{2})")
case class IsoDate(year: Int, month: Int, day: Int)
given RawDecoder[IsoDate] = RawDecoder.derived[IsoDate]

@decodable
@extract("([^<]+?) <([^>]+)>")
case class NamedEmail(displayName: String, address: String)
given RawDecoder[NamedEmail] = RawDecoder.derived[NamedEmail]

@decodable
@extract("[A-Z]{2}-\\d{4}")
case class ProductCode(value: String)
given RawDecoder[ProductCode] = RawDecoder.derived[ProductCode]

@contract
case class Booking(
  @immutable                                    id:        Long,
  @nonEmpty                                     ref:       String,
  @extract("\\d{4}-\\d{2}-\\d{2}")             checkIn:   String,
                                                event:     IsoDate,
                                                host:      Option[NamedEmail],
                                                code:      Option[ProductCode]
)

// ── @validateContract models ──────────────────────────────────────────────────

class CheckOutAfterCheckIn extends ContractValidator[Stay]:
  def validate(s: Stay): List[String] =
    if s.checkOut <= s.checkIn
    then List("checkOut must be after checkIn")
    else Nil

class GuestRangeValid extends ContractValidator[Stay]:
  def validate(s: Stay): List[String] =
    if s.maxGuests < s.minGuests
    then List("maxGuests must be >= minGuests")
    else Nil

@contract
@validateContract(Array(classOf[CheckOutAfterCheckIn], classOf[GuestRangeValid]))
case class Stay(
  @nonEmpty               name:       String,
  @min(1)                 checkIn:    Int,
  @min(1)                 checkOut:   Int,
  @min(1)                 minGuests:  Int,
  @min(1)                 maxGuests:  Int
)

// ── Either sanitization models ────────────────────────────────────────────────
// These have @internal / @masked fields on the branch types so we can verify
// that sanitize() recurses correctly into Either branches.

@contract
case class CardPayment(
  @masked          cardNumber: String,   // should be masked in sanitize output
  @internal        cvv:        String,   // should be stripped in sanitize output
                   amount:     Double
)

@contract
case class BankPayment(
  @nonEmpty        accountRef: String,
  @internal        sortCode:   String,   // should be stripped in sanitize output
                   amount:     Double
)

given RawDecoder[CardPayment] with
  def decode(raw: Any): Option[CardPayment] = raw match
    case m: Map[?, ?] =>
      val sm = m.asInstanceOf[Map[String, Any]]
      for
        cardNumber <- sm.get("cardNumber").flatMap(RawDecoder[String].decode)
        cvv        <- sm.get("cvv").flatMap(RawDecoder[String].decode)
        amount     <- sm.get("amount").flatMap(RawDecoder[Double].decode)
      yield CardPayment(cardNumber, cvv, amount)
    case _ => None

given RawDecoder[BankPayment] with
  def decode(raw: Any): Option[BankPayment] = raw match
    case m: Map[?, ?] =>
      val sm = m.asInstanceOf[Map[String, Any]]
      for
        accountRef <- sm.get("accountRef").flatMap(RawDecoder[String].decode)
        sortCode   <- sm.get("sortCode").flatMap(RawDecoder[String].decode)
        amount     <- sm.get("amount").flatMap(RawDecoder[Double].decode)
      yield BankPayment(accountRef, sortCode, amount)
    case _ => None

@contract
case class Invoice(
  @nonEmpty        invoiceId: String,
  //@discriminator("paymentType", left = "Card", right = "Bank")
                   payment:   Either[CardPayment, BankPayment]
)

given RawDecoder[Invoice] with
  def decode(raw: Any): Option[Invoice] = raw match
    case m: Map[?, ?] =>
      val sm = m.asInstanceOf[Map[String, Any]]
      for
        invoiceId <- sm.get("invoiceId").flatMap(RawDecoder[String].decode)
        payment   <- sm.get("payment").flatMap(RawDecoder[Either[CardPayment, BankPayment]].decode)
      yield Invoice(invoiceId, payment)
    case _ => None

// ── New constraint annotation models ─────────────────────────────────────────

@contract
case class WebLink(
  @url                        href:    String,
  @nonEmpty                   label:   Option[String]
)

@contract
case class Resource(
  @uuid                       id:      String,
  @nonEmpty                   name:    String
)

@contract
case class ScheduledEvent(
  @nonEmpty                   title:   String,
  @future                     startsAt: Long,
  @past                       createdAt: Long
)

@contract
case class Measurement(
  @positive                   value:   Double,
  @positive                   count:   Int
)

@contract
case class Payment(
  @positive @multipleOf(0.01) amount:  Double,
  @multipleOf(5)              quantity: Int
)

// ── List[T] sanitization model ───────────────────────────────────────────────
// Verifies that sanitize() recurses into List[T] fields and strips @internal /
// replaces @masked on every element.

@contract
case class LineItem(
  @nonEmpty   productId: String,
  @internal   costPrice: Double,   // supplier cost — stripped in sanitize
              quantity:  Int
)

given RawDecoder[LineItem] with
  def decode(raw: Any): Option[LineItem] = raw match
    case m: Map[?, ?] =>
      val sm = m.asInstanceOf[Map[String, Any]]
      for
        productId <- sm.get("productId").flatMap(RawDecoder[String].decode)
        costPrice <- sm.get("costPrice").flatMap(RawDecoder[Double].decode)
        quantity  <- sm.get("quantity").flatMap(RawDecoder[Int].decode)
      yield LineItem(productId, costPrice, quantity)
    case _ => None

@contract
case class Cart(
  @nonEmpty   cartId: String,
              items:  List[LineItem]
)

// Contract instances are in TestContracts.scala to avoid Scala 3 top-level
// initialization cycles between Contract.derived macro expansion and the
// given Contract[X] values being defined in the same compilation unit.
