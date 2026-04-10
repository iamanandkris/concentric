package io.dsentric

/**
 * All `Contract.derived` instances for test models.
 *
 * Kept in a separate file from TestModels.scala so that the Scala 3 compiler
 * does not encounter a top-level initialization cycle between the
 * `Contract.derived[X]` macro expansion and the `given Contract[X]` value
 * being defined in the same compilation unit.
 */

given addressContract:    Contract[Address]     = Contract.derived[Address]
given memberContract:     Contract[Member]      = Contract.derived[Member]
given userContract:       Contract[User]        = Contract.derived[User]
given openDocContract:    Contract[OpenDoc]     = Contract.derived[OpenDoc]
given profileContract:    Contract[Profile]     = Contract.derived[Profile]
given ticketContract:     Contract[Ticket]      = Contract.derived[Ticket]
// productContract lives in ConstraintSpec.scala alongside the Product model.
given orderContract:      Contract[Order]       = Contract.derived[Order]
given orderItemContract:  Contract[OrderItem]   = Contract.derived[OrderItem]
given eitherContract:     Contract[EitherHolder]= Contract.derived[EitherHolder]
given apiKeyContract:     Contract[ApiKey]      = Contract.derived[ApiKey]
given prefsContract:      Contract[Prefs]       = Contract.derived[Prefs]
given catContract:        Contract[Cat]         = Contract.derived[Cat]
given dogContract:        Contract[Dog]         = Contract.derived[Dog]
given petHolderContract:  Contract[PetHolder]   = Contract.derived[PetHolder]
given timestampsContract: Contract[Timestamps]  = Contract.derived[Timestamps]
given documentContract:   Contract[Document]    = Contract.derived[Document]
given bookingContract:      Contract[Booking]        = Contract.derived[Booking]
given stayContract:         Contract[Stay]           = Contract.derived[Stay]
given cardPaymentContract:  Contract[CardPayment]    = Contract.derived[CardPayment]
given bankPaymentContract:  Contract[BankPayment]    = Contract.derived[BankPayment]
given invoiceContract:      Contract[Invoice]        = Contract.derived[Invoice]
given webLinkContract:      Contract[WebLink]        = Contract.derived[WebLink]
given resourceContract:     Contract[Resource]       = Contract.derived[Resource]
given scheduledEventContract: Contract[ScheduledEvent] = Contract.derived[ScheduledEvent]
given measurementContract:  Contract[Measurement]    = Contract.derived[Measurement]
given paymentContract:      Contract[Payment]        = Contract.derived[Payment]
given lineItemContract:     Contract[LineItem]       = Contract.derived[LineItem]
given cartContract:         Contract[Cart]           = Contract.derived[Cart]
