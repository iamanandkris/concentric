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
) derives Contract

/** A contract that allows additional properties. */
@contract(open = true)
case class OpenDoc(
  @nonEmpty title: String,
              body:  Option[String]
) derives Contract

// ── Nested address / profile ──────────────────────────────────────────────────

@contract
case class Address(
  @nonEmpty street: String,
  @nonEmpty city:   String
) derives Contract

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
) derives Contract

// ── Nested collection ─────────────────────────────────────────────────────────

@contract
case class OrderItem(
  @nonEmpty         name: String,
  @min(1)           qty:  Int
) derives Contract

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
) derives Contract

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
) derives Contract

// ── Constraint-heavy models ───────────────────────────────────────────────────

@contract
case class Ticket(
  @reserved                           trackingId: String = "PENDING",
  @nonEmpty                           title:      String,
                                      notes:      Option[String]
) derives Contract

// Product is defined in ConstraintSpec.scala — it uses @validateWith which
// causes a Scala 3.4.2 compiler cycle when paired with a top-level
// derived contract alias in a different initialization context.

@contract
case class ApiKey(
  @nonEmpty @minLength(8)      key:     String,
  @masked("REDACTED")          secret:  String,
                               label:   Option[String]
) derives Contract

@contract
case class Prefs(
  language: Option[String],
  timezone: Option[String],
  theme:    Option[String]
) derives Contract

// ── @discriminator models ─────────────────────────────────────────────────────

@contract
case class Cat(
  @nonEmpty name: String,
             indoor: Boolean = true
) derives Contract

@contract
case class Dog(
  @nonEmpty name:  String,
  @min(1)   age:   Int
) derives Contract

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
) derives Contract

// ── @include models ───────────────────────────────────────────────────────────

@contract
case class Timestamps(
  @immutable createdAt: Long,
             updatedAt: Long = 0L
) derives Contract

@contract
case class Document(
  @nonEmpty             title:      String,
                        body:       Option[String],
  @include              timestamps: Timestamps
) derives Contract

given RawDecoder[Timestamps] with
  def decode(raw: Any): Option[Timestamps] = raw match
    case m: Map[?, ?] =>
      val sm = m.asInstanceOf[Map[String, Any]]
      for ca <- sm.get("createdAt").flatMap(RawDecoder[Long].decode)
      yield Timestamps(ca, sm.get("updatedAt").flatMap(RawDecoder[Long].decode).getOrElse(0L))
    case _ => None

@contract
case class AuditStamp(
  @include              timestamps: Timestamps,
                        createdBy:  String
) derives Contract

@contract
case class DeepDocument(
  @nonEmpty             title: String,
  @include              audit: AuditStamp
) derives Contract

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
) derives Contract

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
) derives Contract

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
) derives Contract

// ── Either sanitization models ────────────────────────────────────────────────
// These have @internal / @masked fields on the branch types so we can verify
// that sanitize() recurses correctly into Either branches.

@contract
case class CardPayment(
  @masked          cardNumber: String,   // should be masked in sanitize output
  @internal        cvv:        String,   // should be stripped in sanitize output
                   amount:     Double
) derives Contract

@contract
case class BankPayment(
  @nonEmpty        accountRef: String,
  @internal        sortCode:   String,   // should be stripped in sanitize output
                   amount:     Double
) derives Contract

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
) derives Contract

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
) derives Contract

@contract
case class Resource(
  @uuid                       id:      String,
  @nonEmpty                   name:    String
) derives Contract

@contract
case class ScheduledEvent(
  @nonEmpty                   title:   String,
  @future                     startsAt: Long,
  @past                       createdAt: Long
) derives Contract

@contract
case class Measurement(
  @positive                   value:   Double,
  @positive                   count:   Int
) derives Contract

@contract
case class Payment(
  @positive @multipleOf(0.01) amount:  Double,
  @multipleOf(5)              quantity: Int
) derives Contract

// ── List[T] sanitization model ───────────────────────────────────────────────
// Verifies that sanitize() recurses into List[T] fields and strips @internal /
// replaces @masked on every element.

@contract
case class LineItem(
  @nonEmpty   productId: String,
  @internal   costPrice: Double,   // supplier cost — stripped in sanitize
              quantity:  Int
) derives Contract

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
) derives Contract
