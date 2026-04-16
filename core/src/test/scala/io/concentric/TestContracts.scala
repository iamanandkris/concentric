package io.concentric

/**
 * Convenient aliases for summoned contract instances used throughout tests.
 *
 * The contracts themselves are now derived directly on the model case classes
 * via `derives Contract`; these vals only preserve the long-standing test API.
 */
val addressContract:    Contract[Address]        = summon[Contract[Address]]
val memberContract:     Contract[Member]         = summon[Contract[Member]]
val userContract:       Contract[User]           = summon[Contract[User]]
val openDocContract:    Contract[OpenDoc]        = summon[Contract[OpenDoc]]
val profileContract:    Contract[Profile]        = summon[Contract[Profile]]
val ticketContract:     Contract[Ticket]         = summon[Contract[Ticket]]
val orderContract:      Contract[Order]          = summon[Contract[Order]]
val orderItemContract:  Contract[OrderItem]      = summon[Contract[OrderItem]]
val eitherContract:     Contract[EitherHolder]   = summon[Contract[EitherHolder]]
val apiKeyContract:     Contract[ApiKey]         = summon[Contract[ApiKey]]
val prefsContract:      Contract[Prefs]          = summon[Contract[Prefs]]
val catContract:        Contract[Cat]            = summon[Contract[Cat]]
val dogContract:        Contract[Dog]            = summon[Contract[Dog]]
val petHolderContract:  Contract[PetHolder]      = summon[Contract[PetHolder]]
val timestampsContract: Contract[Timestamps]     = summon[Contract[Timestamps]]
val documentContract:   Contract[Document]       = summon[Contract[Document]]
val bookingContract:    Contract[Booking]        = summon[Contract[Booking]]
val stayContract:       Contract[Stay]           = summon[Contract[Stay]]
val cardPaymentContract: Contract[CardPayment]   = summon[Contract[CardPayment]]
val bankPaymentContract: Contract[BankPayment]   = summon[Contract[BankPayment]]
val invoiceContract:    Contract[Invoice]        = summon[Contract[Invoice]]
val webLinkContract:    Contract[WebLink]        = summon[Contract[WebLink]]
val resourceContract:   Contract[Resource]       = summon[Contract[Resource]]
val scheduledEventContract: Contract[ScheduledEvent] = summon[Contract[ScheduledEvent]]
val measurementContract: Contract[Measurement]   = summon[Contract[Measurement]]
val paymentContract:    Contract[Payment]        = summon[Contract[Payment]]
val lineItemContract:   Contract[LineItem]       = summon[Contract[LineItem]]
val cartContract:       Contract[Cart]           = summon[Contract[Cart]]
