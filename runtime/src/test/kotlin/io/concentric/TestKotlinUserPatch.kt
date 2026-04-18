package io.concentric

import io.concentric.annotations.*
import java.util.Optional

/**
 * Kotlin data class aspect for TestKotlinUser — used in JvmAspectSpec.
 *
 * @field:nonEmpty / @field:maxLength(50) are inherited from TestKotlinUser.name.
 * @field:email is inherited from TestKotlinUser.email.
 *
 * age and id are excluded by omission.
 */
@aspectOf(TestKotlinUser::class.java)
data class TestKotlinUserPatch(
    val name:  Optional<String> = Optional.empty(),  // @nonEmpty @maxLength(50) inherited
    val email: Optional<String> = Optional.empty()   // @email inherited
)
