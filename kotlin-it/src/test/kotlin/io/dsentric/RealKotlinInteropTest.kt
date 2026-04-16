package io.dsentric

import io.dsentric.annotations.contract
import io.dsentric.annotations.email
import io.dsentric.annotations.internal
import io.dsentric.annotations.masked
import io.dsentric.annotations.nonEmpty
import io.dsentric.annotations.reserved
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.LinkedHashMap
import java.util.Optional

class RealKotlinInteropTest {

    @Test
    fun kotlinPrimaryContractShouldDecodeNestedDataClasses() {
        val contract = JvmContract.ofPrimary(RealKotlinUserPayload::class.java)
        val payload = linkedMapOf<String, Any>(
            "email" to "kotlin@example.com",
            "name" to "Kotlin User",
            "address" to mapOf("street" to "1 Kotlin St", "city" to "Leeds", "zipCode" to "LS11AA"),
            "preferences" to mapOf("newsletter" to true),
        )

        val result = contract.validate(LinkedHashMap(payload))
        assertTrue(result.isValid)
        val value = result.getValue().orElseThrow()
        assertEquals("1 Kotlin St", value.address!!.street)
        assertEquals(true, value.preferences!!.newsletter)
    }

    @Test
    fun kotlinPrimaryContractShouldTreatOptionalAnnotatedFieldsAsOptional() {
        val contract = JvmContract.ofPrimary(RealKotlinUserPayload::class.java)
        val payload = linkedMapOf<String, Any>(
            "email" to "optional@example.com",
            "name" to "Optional Kotlin User",
        )

        val result = contract.validate(LinkedHashMap(payload))
        assertTrue("expected omitted optional fields not to be treated as required", result.isValid)
    }

    @Test
    fun kotlinPrimaryContractShouldRejectNestedReservedField() {
        val contract = JvmContract.ofPrimary(RealKotlinUserPayload::class.java)
        val payload = linkedMapOf<String, Any>(
            "email" to "reserved@example.com",
            "name" to "Reserved Kotlin User",
            "preferences" to mapOf("internalSegment" to "secret"),
        )

        val result = contract.validate(LinkedHashMap(payload))
        assertFalse(result.isValid)
        assertTrue(result.getErrors().any { it.path.contains("internalSegment") })
    }

    @Test
    fun kotlinPrimaryContractShouldMaskAndDropNestedSensitiveFieldsOnSanitize() {
        val contract = JvmContract.ofPrimary(RealKotlinOrderPayload::class.java)
        val payload = linkedMapOf<String, Any>(
            "userId" to 1L,
            "orderNumber" to "ORD-KOTLIN-001",
            "status" to "pending",
            "items" to listOf(mapOf("productId" to 1L, "sku" to "SKU-1", "quantity" to 2, "unitPrice" to 10.0)),
            "totals" to mapOf("subtotal" to 20.0, "tax" to 2.0, "shipping" to 1.0, "total" to 23.0),
            "paymentInfo" to mapOf("method" to "credit_card", "last4" to "1234", "gatewayReference" to "gw-123"),
        )

        val sanitized = contract.sanitize(LinkedHashMap(payload))
        @Suppress("UNCHECKED_CAST")
        val paymentInfo = sanitized["paymentInfo"] as Map<String, Any?>?

        assertNotNull(paymentInfo)
        assertEquals("****", paymentInfo!!["last4"])
        assertFalse(paymentInfo.containsKey("gatewayReference"))
    }

    @Test
    fun kotlinOpenContractShouldPreserveExtras() {
        val contract = JvmOpenContract.ofPrimary(RealKotlinOpenMetadata::class.java)
        val payload = linkedMapOf<String, Any>(
            "key" to "theme",
            "value" to "dark",
            "source" to "ui-settings",
        )

        val result = contract.validate(LinkedHashMap(payload))
        assertTrue(result.isValid)
        assertEquals("theme", result.value.orElseThrow().key)
        assertEquals("ui-settings", result.extras["source"])
    }
}

@contract
data class RealKotlinAddress(
    val street: String? = null,
    val city: String? = null,
    val zipCode: String? = null,
)

@contract
data class RealKotlinPreferences(
    val newsletter: Boolean? = null,
    @param:reserved @field:reserved val internalSegment: Optional<String> = Optional.empty(),
)

@contract
data class RealKotlinOrderItem(
    val productId: Long,
    val sku: String,
    val quantity: Int,
    val unitPrice: Double,
)

@contract
data class RealKotlinTotals(
    val subtotal: Double,
    val tax: Double,
    val shipping: Double,
    val total: Double,
)

@contract
data class RealKotlinPaymentInfo(
    val method: String,
    @param:masked("****") @field:masked("****") val last4: String,
    @param:internal @field:internal val gatewayReference: Optional<String> = Optional.empty(),
)

@contract
data class RealKotlinUserPayload(
    @param:email @field:email val email: String,
    @param:nonEmpty @field:nonEmpty val name: String,
    val address: RealKotlinAddress? = null,
    val preferences: RealKotlinPreferences? = null,
    @param:internal @field:internal val internalNotes: Optional<String> = Optional.empty(),
)

@contract
data class RealKotlinOrderPayload(
    val userId: Long,
    val orderNumber: String,
    val status: String,
    val items: List<RealKotlinOrderItem>,
    val totals: RealKotlinTotals,
    val paymentInfo: RealKotlinPaymentInfo,
)

@contract
data class RealKotlinOpenMetadata(
    @param:nonEmpty @field:nonEmpty val key: String,
    @param:nonEmpty @field:nonEmpty val value: String,
)
